package com.pharmabill.app.dataset

import com.pharmabill.app.domain.model.BBox
import java.util.Locale

object YoloFormat {
    /** "class cx cy w h", all normalised to 0..1. */
    fun label(classIndex: Int, box: BBox, imageWidth: Int, imageHeight: Int): String {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        fun f(v: Float) = String.format(Locale.US, "%.6f", v.coerceIn(0f, 1f))
        return "$classIndex ${f(box.cx / w)} ${f(box.cy / h)} ${f(box.width / w)} ${f(box.height / h)}"
    }

    fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
