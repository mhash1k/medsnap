package com.pharmabill.app.ai.match

import kotlin.math.max
import kotlin.math.min

object JaroWinkler {
    fun similarity(a: String, b: String): Double {
        if (a == b) return if (a.isEmpty()) 0.0 else 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = max(0, max(a.length, b.length) / 2 - 1)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val start = max(0, i - range)
            val end = min(b.length - 1, i + range)
            for (j in start..end) {
                if (bMatched[j] || a[i] != b[j]) continue
                aMatched[i] = true
                bMatched[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3.0
        var prefix = 0
        for (i in 0 until min(4, min(a.length, b.length))) {
            if (a[i] == b[i]) prefix++ else break
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }
}

object Levenshtein {
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
