package com.pharmabill.app.device.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PrinterDevice(val name: String, val address: String)

/** ESC/POS over Bluetooth SPP. Receipts are sent as raster images so Bangla text prints on any 58 mm printer. */
object EscPos {
    private val INIT = byteArrayOf(0x1B, 0x40)
    private val FEED_CUT = byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00)

    fun raster(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArrayOutputStream()
        out.write(INIT)
        val pixels = IntArray(w)
        var y0 = 0
        while (y0 < h) {
            val band = minOf(255, h - y0)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (bytesPerRow and 0xFF).toByte(), (bytesPerRow shr 8).toByte(), (band and 0xFF).toByte(), (band shr 8).toByte()))
            for (y in y0 until y0 + band) {
                bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
                val row = ByteArray(bytesPerRow)
                for (x in 0 until w) {
                    val p = pixels[x]
                    val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    if (lum < 128) row[x / 8] = (row[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
                out.write(row)
            }
            y0 += band
        }
        out.write(FEED_CUT)
        return out.toByteArray()
    }
}

@Singleton
class PrinterManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    fun hasPermission(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun adapter() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bluetoothOn(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PrinterDevice> {
        if (!hasPermission()) return emptyList()
        return adapter()?.bondedDevices.orEmpty().map { PrinterDevice(it.name ?: it.address, it.address) }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    suspend fun print(address: String, bitmap: Bitmap): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasPermission()) { "Bluetooth permission missing" }
            val a = adapter() ?: error("No Bluetooth")
            val device = a.getRemoteDevice(address)
            a.cancelDiscovery()
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(spp)
                socket.connect()
                val data = EscPos.raster(bitmap)
                val os = socket.outputStream
                var i = 0
                while (i < data.size) {
                    val n = minOf(1024, data.size - i)
                    os.write(data, i, n)
                    os.flush()
                    i += n
                    Thread.sleep(20)
                }
                Thread.sleep(300)
            } finally {
                runCatching { socket?.close() }
            }
        }
    }
}
