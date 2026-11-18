package com.pharmabill.app.ai.image

/** Blur and glare checks on a luminance (0..255) grid. */
object ImageQuality {
    const val BLUR_VARIANCE_MIN = 60.0
    const val GLARE_FRACTION_MAX = 0.08
    const val GLARE_LEVEL = 250

    fun laplacianVariance(lum: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val v = (lum[i - width] + lum[i + width] + lum[i - 1] + lum[i + 1] - 4 * lum[i]).toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    fun overexposedFraction(lum: IntArray, level: Int = GLARE_LEVEL): Double {
        if (lum.isEmpty()) return 0.0
        return lum.count { it >= level }.toDouble() / lum.size
    }

    fun isBlurry(lum: IntArray, width: Int, height: Int): Boolean = laplacianVariance(lum, width, height) < BLUR_VARIANCE_MIN

    fun hasGlare(lum: IntArray): Boolean = overexposedFraction(lum) > GLARE_FRACTION_MAX
}
