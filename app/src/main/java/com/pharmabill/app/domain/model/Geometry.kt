package com.pharmabill.app.domain.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Plain bounding box in image pixels. Used instead of android.graphics.RectF so the logic runs in JVM tests. */
data class BBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val area: Float get() = max(0f, width) * max(0f, height)

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun expand(by: Float) = BBox(left - by, top - by, right + by, bottom + by)

    fun union(o: BBox) = BBox(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun iou(o: BBox): Float {
        val iw = min(right, o.right) - max(left, o.left)
        val ih = min(bottom, o.bottom) - max(top, o.top)
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val u = area + o.area - inter
        return if (u <= 0f) 0f else inter / u
    }

    /** Distance from a point to the box edge (0 when inside). */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = max(max(left - x, 0f), x - right)
        val dy = max(max(top - y, 0f), y - bottom)
        return sqrt(dx * dx + dy * dy)
    }
}

data class Detection(
    val cls: DetClass,
    val box: BBox,
    val confidence: Float,
)
