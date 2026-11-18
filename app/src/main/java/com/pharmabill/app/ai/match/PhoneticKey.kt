package com.pharmabill.app.ai.match

/**
 * A rough phonetic key for Bangla-accented brand names ("napa" = "nappa", "seclo" = "seklo").
 * Keeps the first letter, drops later vowels, merges similar consonants and collapses repeats.
 */
object PhoneticKey {
    private val digraphs = listOf("ph" to "f", "kh" to "k", "gh" to "g", "th" to "t", "dh" to "d", "sh" to "s", "bh" to "b", "ck" to "k")

    fun of(input: String): String {
        var s = input.lowercase().filter { it in 'a'..'z' }
        if (s.isEmpty()) return ""
        for ((from, to) in digraphs) s = s.replace(from, to)
        val mapped = s.map {
            when (it) {
                'c', 'q' -> 'k'
                'z' -> 'j'
                'v', 'w' -> 'b'
                'y' -> 'i'
                'x' -> 'k'
                else -> it
            }
        }
        val out = StringBuilder()
        mapped.forEachIndexed { i, c ->
            val vowel = c in "aeiou"
            if (i > 0 && vowel) return@forEachIndexed
            if (out.isNotEmpty() && out.last() == c) return@forEachIndexed
            out.append(c)
        }
        return out.toString()
    }
}
