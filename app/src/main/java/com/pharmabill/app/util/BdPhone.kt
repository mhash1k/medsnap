package com.pharmabill.app.util

/**
 * Bangladeshi mobile numbers: 11 digits, 01 + operator digit 3-9 + 8 digits (e.g. 01712345678).
 * Accepts Bangla digits, spaces/dashes and a +880 / 880 / 0088 prefix while typing, and always stores the local 11-digit form.
 */
object BdPhone {
    const val LENGTH = 11
    private val valid = Regex("^01[3-9]\\d{8}$")

    /** Keeps digits only (Bangla digits converted), drops a country prefix, caps at 11 digits. Use on every keystroke. */
    fun clean(input: String): String {
        var d = input.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.filter { it.isDigit() }.joinToString("")
        when {
            d.startsWith("00880") -> d = d.removePrefix("0088")
            d.startsWith("880") -> d = d.removePrefix("88")
            d.startsWith("88") && d.length > 2 && d[2] == '0' -> d = d.removePrefix("88")
        }
        return d.take(LENGTH)
    }

    fun isValid(input: String): Boolean = valid.matches(clean(input))

    enum class Problem { EMPTY, NOT_01, BAD_OPERATOR, TOO_SHORT }

    /** Why the number is not accepted yet, or null when it is valid. */
    fun problem(input: String): Problem? {
        val d = clean(input)
        return when {
            d.isEmpty() -> Problem.EMPTY
            !d.startsWith("0") || (d.length >= 2 && d[1] != '1') -> Problem.NOT_01
            d.length >= 3 && d[2] !in '3'..'9' -> Problem.BAD_OPERATOR
            d.length < LENGTH -> Problem.TOO_SHORT
            else -> null
        }
    }
}
