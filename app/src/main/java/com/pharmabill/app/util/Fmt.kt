package com.pharmabill.app.util

object Fmt {
    fun toBanglaDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') '০' + (c - '0') else c)
    }

    fun digits(s: String, bangla: Boolean): String = if (bangla) toBanglaDigits(s) else s

    /** 1250 paisa -> "12.50". */
    fun paisaToPlain(paisa: Long): String {
        val neg = paisa < 0
        val abs = kotlin.math.abs(paisa)
        val s = "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
        return if (neg) "-$s" else s
    }

    fun taka(paisa: Long, bangla: Boolean): String = "৳" + digits(paisaToPlain(paisa), bangla)

    /** Whole-Taka display for totals: 1200 -> "৳12". */
    fun takaWhole(paisa: Long, bangla: Boolean): String = "৳" + digits((paisa / 100).toString(), bangla)

    fun number(n: Long, bangla: Boolean): String = digits(n.toString(), bangla)

    /** Parses "12", "12.5", "১২.৫০" into paisa. */
    fun parseTaka(text: String): Long? {
        val t = text.trim().map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("").replace(",", "")
        if (t.isEmpty()) return null
        val parts = t.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val frac = if (parts.size == 2) parts[1].take(2).padEnd(2, '0').toLongOrNull() ?: return null else 0L
        return whole * 100 + frac
    }

    fun parseIntLocal(text: String): Int? =
        text.trim().map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("").toIntOrNull()
}
