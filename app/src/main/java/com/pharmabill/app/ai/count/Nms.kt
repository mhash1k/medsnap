package com.pharmabill.app.ai.count

import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.DetClass
import com.pharmabill.app.domain.model.Detection
import kotlin.math.max
import kotlin.math.min

object Nms {
    /** Class-wise non-maximum suppression. */
    fun apply(dets: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val out = mutableListOf<Detection>()
        dets.groupBy { it.cls }.forEach { (_, list) ->
            val sorted = list.sortedByDescending { it.confidence }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                out += best
                sorted.removeAll { it.box.iou(best.box) > iouThreshold }
            }
        }
        return out.sortedByDescending { it.confidence }
    }
}

data class Letterbox(val scale: Float, val padX: Float, val padY: Float, val size: Int) {
    fun toImage(x: Float, y: Float): Pair<Float, Float> = ((x - padX) / scale) to ((y - padY) / scale)

    companion object {
        fun compute(srcW: Int, srcH: Int, size: Int): Letterbox {
            val scale = min(size.toFloat() / srcW, size.toFloat() / srcH)
            val newW = srcW * scale
            val newH = srcH * scale
            return Letterbox(scale, (size - newW) / 2f, (size - newH) / 2f, size)
        }
    }
}

/**
 * Decodes a YOLOv8/YOLO11 TFLite output: shape [1, 4 + classes, N] (channels first) or [1, N, 4 + classes].
 * Boxes are centre-x, centre-y, width, height, either normalised (0..1) or in input pixels.
 */
object YoloDecoder {
    fun decode(
        output: FloatArray,
        shape: IntArray,
        numClasses: Int,
        confThreshold: Float,
        letterbox: Letterbox,
        imageWidth: Int,
        imageHeight: Int,
    ): List<Detection> {
        require(shape.size == 3) { "unexpected output rank ${shape.size}" }
        val channels = 4 + numClasses
        val channelsFirst = shape[1] == channels
        val n = if (channelsFirst) shape[2] else shape[1]
        fun at(anchor: Int, ch: Int): Float =
            if (channelsFirst) output[ch * n + anchor] else output[anchor * channels + ch]

        var maxCoord = 0f
        for (a in 0 until n) for (c in 0 until 4) maxCoord = max(maxCoord, at(a, c))
        val mul = if (maxCoord <= 2f) letterbox.size.toFloat() else 1f

        val out = mutableListOf<Detection>()
        for (a in 0 until n) {
            var bestC = -1
            var bestS = 0f
            for (c in 0 until numClasses) {
                val s = at(a, 4 + c)
                if (s > bestS) { bestS = s; bestC = c }
            }
            if (bestC < 0 || bestS < confThreshold) continue
            val cls = DetClass.fromIndex(bestC) ?: continue
            val cx = at(a, 0) * mul
            val cy = at(a, 1) * mul
            val w = at(a, 2) * mul
            val h = at(a, 3) * mul
            val (l, t) = letterbox.toImage(cx - w / 2, cy - h / 2)
            val (r, b) = letterbox.toImage(cx + w / 2, cy + h / 2)
            out += Detection(
                cls,
                BBox(
                    l.coerceIn(0f, imageWidth.toFloat()), t.coerceIn(0f, imageHeight.toFloat()),
                    r.coerceIn(0f, imageWidth.toFloat()), b.coerceIn(0f, imageHeight.toFloat()),
                ),
                bestS,
            )
        }
        return out
    }
}
