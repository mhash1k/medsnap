package com.pharmabill.app.ai.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pharmabill.app.domain.model.BBox
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLine(val text: String, val box: BBox)
data class OcrBlock(val text: String, val box: BBox, val lines: List<OcrLine>)

interface TextRecognizer {
    suspend fun recognize(bitmap: Bitmap): List<OcrBlock>
}

/** ML Kit Text Recognition v2, on-device Latin model bundled in the APK. */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(bitmap: Bitmap): List<OcrBlock> = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val blocks = text.textBlocks.map { b ->
                    OcrBlock(
                        text = b.text,
                        box = b.boundingBox.toBBox(),
                        lines = b.lines.map { l -> OcrLine(l.text, l.boundingBox.toBBox()) },
                    )
                }
                if (cont.isActive) cont.resume(blocks)
            }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    private fun Rect?.toBBox(): BBox =
        if (this == null) BBox(0f, 0f, 0f, 0f) else BBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}
