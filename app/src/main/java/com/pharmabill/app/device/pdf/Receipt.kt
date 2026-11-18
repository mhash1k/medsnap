package com.pharmabill.app.device.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.ui.components.QtyText
import com.pharmabill.app.util.Fmt
import com.pharmabill.app.util.LocaleHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptRow(val name: String, val qty: String, val amount: String)

data class Receipt(
    val shopName: String,
    val shopAddress: String,
    val shopPhone: String,
    val billNoText: String,
    val dateText: String,
    val rows: List<ReceiptRow>,
    val subtotalLabel: String,
    val subtotal: String,
    val discountLabel: String?,
    val discount: String?,
    val totalLabel: String,
    val total: String,
    val footer: String,
)

/** Builds the receipt text in the shop's language, shared by the PDF and the thermal printer. */
object ReceiptBuilder {
    fun build(context: Context, settings: AppSettings, bill: BillEntity, lines: List<BillLineEntity>, piecesPerStrip: (Long?) -> Int): Receipt {
        val c = LocaleHelper.wrap(context, settings.language)
        val bn = settings.isBangla
        val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)
            .format(Date(bill.confirmedAt ?: bill.createdAt))
        val rows = lines.map {
            ReceiptRow(
                name = it.displayName,
                qty = QtyText.plain(c, it.saleUnit, it.quantity, piecesPerStrip(it.medicineId), bn),
                amount = Fmt.taka(it.lineTotal, bn),
            )
        }
        val disc = PriceCalculator.discountAmount(bill.subtotal, bill.discountType, bill.discountValue)
        val discLabel = when (bill.discountType) {
            DiscountType.PERCENT -> c.getString(R.string.receipt_discount_percent, Fmt.digits(Fmt.paisaToPlain(bill.discountValue).removeSuffix(".00"), bn))
            DiscountType.FLAT -> c.getString(R.string.discount)
            null -> null
        }
        return Receipt(
            shopName = settings.shopName.ifBlank { c.getString(R.string.app_name) },
            shopAddress = settings.shopAddress,
            shopPhone = settings.shopPhone,
            billNoText = c.getString(R.string.receipt_bill_no, Fmt.digits(billNo(bill), bn)),
            dateText = date,
            rows = rows,
            subtotalLabel = c.getString(R.string.subtotal),
            subtotal = Fmt.taka(bill.subtotal, bn),
            discountLabel = if (disc > 0) discLabel else null,
            discount = if (disc > 0) "-" + Fmt.taka(disc, bn) else null,
            totalLabel = c.getString(R.string.total),
            total = Fmt.takaWhole(bill.total, bn),
            footer = c.getString(R.string.receipt_thanks),
        )
    }

    fun billNo(bill: BillEntity): String = bill.billNo?.let { "B-" + it.toString().padStart(6, '0') } ?: "D-${bill.id}"
}

/** Draws a receipt on any canvas; returns the height used. Width is in canvas units. */
object ReceiptLayout {
    fun draw(canvas: Canvas?, width: Float, r: Receipt, scale: Float): Float {
        val pad = 6f * scale
        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f * scale }
        val bold = TextPaint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val big = TextPaint(bold).apply { textSize = 12f * scale }
        val line = 1.35f
        var y = pad

        fun centered(text: String, p: TextPaint) {
            if (text.isBlank()) return
            y += p.textSize * line
            canvas?.drawText(text, (width - p.measureText(text)) / 2f, y, p)
        }
        fun leftRight(left: String, right: String, p: TextPaint) {
            y += p.textSize * line
            val rw = p.measureText(right)
            val maxLeft = width - 2 * pad - rw - 4 * scale
            var l = left
            while (l.isNotEmpty() && p.measureText(l) > maxLeft) l = l.dropLast(1)
            canvas?.drawText(l, pad, y, p)
            canvas?.drawText(right, width - pad - rw, y, p)
        }
        fun rule() {
            y += 4f * scale
            canvas?.drawLine(pad, y, width - pad, y, normal)
            y += 2f * scale
        }

        centered(r.shopName, big)
        centered(r.shopAddress, normal)
        centered(r.shopPhone, normal)
        rule()
        leftRight(r.billNoText, "", normal)
        leftRight(r.dateText, "", normal)
        rule()
        for (row in r.rows) {
            leftRight(row.name, row.amount, bold)
            leftRight("  " + row.qty, "", normal)
        }
        rule()
        leftRight(r.subtotalLabel, r.subtotal, normal)
        if (r.discountLabel != null && r.discount != null) leftRight(r.discountLabel, r.discount, normal)
        leftRight(r.totalLabel, r.total, big)
        rule()
        centered(r.footer, normal)
        return y + pad * 2
    }

    /** Monochrome-friendly bitmap for 58 mm printers (384 dots wide). */
    fun bitmap(r: Receipt, widthPx: Int = 384): Bitmap {
        val scale = widthPx / 164f
        val h = draw(null, widthPx.toFloat(), r, scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        draw(c, widthPx.toFloat(), r, scale)
        return bmp
    }
}

/** Android PdfDocument, 58 mm wide page (164 pt), height fitted to the content. */
object ReceiptPdfGenerator {
    fun generate(context: Context, r: Receipt, fileName: String): File {
        val width = 164f
        val height = ReceiptLayout.draw(null, width, r, 1f)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt().coerceAtLeast(100), 1).create())
        ReceiptLayout.draw(page.canvas, width, r, 1f)
        doc.finishPage(page)
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "$fileName.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
