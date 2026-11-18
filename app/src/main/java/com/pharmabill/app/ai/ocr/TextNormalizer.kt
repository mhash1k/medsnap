package com.pharmabill.app.ai.ocr

data class Strength(val value: String, val unit: String) {
    val key: String get() = value + unit
}

object TextNormalizer {
    private val strengthRegex = Regex("""(\d+(?:\.\d+)?)\s?(MCG|MG|ML|G|IU|%)(?![A-Z])""")
    private val noiseRegex = Regex(
        """\b(BATCH|B\.?\s?NO|LOT|MFG|MFD|EXP|EXPIRY|MANUFACTURED|RX\s?ONLY|MRP|PRICE|TK|TAKA|DAR|REG|DOSAGE|STORE|KEEP|USE\s?BEFORE)\b"""
    )
    private val dateRegex = Regex("""\b\d{1,2}[/.-]\d{2,4}\b""")
    private val priceRegex = Regex("""(৳|TK\.?)\s?\d""")

    /** Words that describe the product but are never part of a brand. */
    val stopWords = setOf(
        "TABLET", "TABLETS", "TAB", "CAPSULE", "CAPSULES", "CAP", "SYRUP", "SUSPENSION", "INJECTION", "INJ",
        "CREAM", "OINTMENT", "DROPS", "DROP", "EYE", "EAR", "BP", "USP", "IP", "MG", "ML", "MCG", "G", "IU",
        "EACH", "FILM", "COATED", "CONTAINS", "LTD", "LIMITED", "PHARMACEUTICALS", "PHARMA", "PLC", "BANGLADESH",
        "THE", "AND", "OF", "FOR", "ORAL", "USE", "ONLY", "STRIP", "STRIPS", "X", "PCS",
    )

    fun isNoise(line: String): Boolean {
        val u = line.uppercase()
        if (noiseRegex.containsMatchIn(u)) return true
        if (priceRegex.containsMatchIn(u)) return true
        val letters = u.count { it.isLetter() }
        val digits = u.count { it.isDigit() }
        if (letters == 0) return true
        if (dateRegex.containsMatchIn(u) && letters < 4) return true
        return digits > letters * 2
    }

    fun strengths(text: String): List<Strength> =
        strengthRegex.findAll(text.uppercase()).map {
            val v = it.groupValues[1].trimEnd('0').trimEnd('.').ifEmpty { "0" }
            val value = if (it.groupValues[1].contains('.')) v else it.groupValues[1]
            Strength(value, it.groupValues[2])
        }.toList()

    fun firstStrengthKey(text: String): String? = strengths(text).firstOrNull()?.key

    /** Uppercase, strip symbols, keep letters/digits/./%; then fix OCR swaps inside brand-like words. */
    fun tokens(text: String): List<String> {
        val cleaned = text.uppercase().replace(Regex("""[^A-Z0-9.%\s-]"""), " ").replace('-', ' ')
        val withoutStrength = strengthRegex.replace(cleaned, " ")
        return withoutStrength.split(Regex("""\s+"""))
            .map { it.trim('.') }
            .filter { it.isNotEmpty() }
            .map { fixOcrSwaps(it) }
    }

    fun brandTokens(text: String): List<String> =
        tokens(text).filter { it !in stopWords && it.any { c -> c.isLetter() } && it.length >= 2 }

    /** 0->O, 1->I, 5->S only when the token is mostly letters (a brand word), never in numbers. */
    fun fixOcrSwaps(token: String): String {
        val letters = token.count { it.isLetter() }
        val digits = token.count { it.isDigit() }
        if (letters < 2 || digits == 0 || digits > letters) return token
        return token.map {
            when (it) {
                '0' -> 'O'
                '1' -> 'I'
                '5' -> 'S'
                else -> it
            }
        }.joinToString("")
    }

    fun normalizeBrand(brand: String): String = tokens(brand).joinToString(" ")
}
