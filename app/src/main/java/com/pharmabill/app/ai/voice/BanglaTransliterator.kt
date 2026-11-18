package com.pharmabill.app.ai.voice

/**
 * Turns Bangla script from the speech recogniser into rough Latin letters so the same parser and
 * fuzzy matcher work for "নাপা দুইটা" and "napa duita". Common words use a fixed dictionary.
 */
object BanglaTransliterator {

    private val dictionary: Map<String, String> = mapOf(
        "এক" to "ek", "একটা" to "ekta", "একটি" to "ekti", "দুই" to "dui", "দুইটা" to "duita", "দুইটি" to "duiti",
        "দুটো" to "duto", "দুটা" to "duta", "দুটি" to "duti", "তিন" to "tin", "তিনটা" to "tinta", "তিনটি" to "tinti",
        "চার" to "char", "চারটা" to "charta", "চারটি" to "charti", "পাঁচ" to "pach", "পাঁচটা" to "pachta",
        "পাঁচটি" to "pachti", "ছয়" to "choy", "ছয়টা" to "choyta", "ছয়" to "choy", "সাত" to "shat", "সাতটা" to "shatta",
        "আট" to "aat", "আটটা" to "aatta", "নয়" to "noy", "নয়" to "noy", "নয়টা" to "noyta", "দশ" to "dosh",
        "দশটা" to "doshta", "এগারো" to "egaro", "বারো" to "baro", "তেরো" to "tero", "চৌদ্দ" to "choddo",
        "পনেরো" to "ponero", "ষোলো" to "sholo", "সতেরো" to "shotero", "আঠারো" to "atharo", "উনিশ" to "unish",
        "বিশ" to "bish", "ত্রিশ" to "tirish",
        "পাতা" to "pata", "পাত্তা" to "patta", "স্ট্রিপ" to "strip", "বক্স" to "box", "বাক্স" to "bakso",
        "প্যাকেট" to "packet", "বোতল" to "botol", "শিশি" to "shishi", "পিস" to "pis", "টা" to "ta", "টি" to "ti",
        "ট্যাবলেট" to "tablet", "আধা" to "adha", "অর্ধেক" to "adha", "হাফ" to "half",
        "বাদ" to "bad", "কেটে" to "kete", "রিমুভ" to "remove", "দাও" to "dao", "দিন" to "din", "দেন" to "den",
        "কত" to "koto", "কয়টা" to "koyta", "কয়টা" to "koyta", "কয়টি" to "koyti", "আছে" to "ache",
        "স্টক" to "stock", "দাম" to "dam", "টাকা" to "taka", "প্রাইস" to "price", "বিল" to "bill",
        "করো" to "koro", "কর" to "koro", "করেন" to "koro", "করুন" to "koro", "করে" to "kore", "হবে" to "hobe",
        "চেঞ্জ" to "change", "শেষ" to "shesh", "নতুন" to "notun", "কনফার্ম" to "confirm", "আর" to "ar", "ও" to "o",
        "এর" to "er", "আমাকে" to "amake", "প্লিজ" to "please", "এবং" to "ar",
    )

    private val independentVowels = mapOf(
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri",
        'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou",
    )
    private val vowelSigns = mapOf(
        'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u", 'ৃ' to "ri",
        'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou",
    )
    private val consonants = mapOf(
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng", 'চ' to "ch", 'ছ' to "chh", 'জ' to "j",
        'ঝ' to "jh", 'ঞ' to "n", 'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n", 'ত' to "t",
        'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n", 'প' to "p", 'ফ' to "f", 'ব' to "b", 'ভ' to "bh",
        'ম' to "m", 'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh", 'স' to "s", 'হ' to "h",
        '\u09DC' to "r", '\u09DD' to "rh", '\u09DF' to "y", 'ৎ' to "t",
    )
    private const val HASANTA = '্'
    private const val NUKTA = '়'

    fun isBangla(c: Char): Boolean = c in 'ঀ'..'৿'

    fun containsBangla(s: String): Boolean = s.any { isBangla(it) }

    fun convertDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '০'..'৯') '0' + (c - '০') else c)
    }

    /** Normalises the composed/decomposed forms of ড় ঢ় য় so lookups are stable. */
    private fun compose(s: String): String =
        s.replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD").replace("\u09AF\u09BC", "\u09DF")

    fun word(raw: String): String {
        val w = compose(convertDigits(raw))
        dictionary[w]?.let { return it }
        if (!containsBangla(w)) return w.lowercase()
        // Dictionary suffix forms: "নাপার" -> "napa" + "r"; "দুইটা" style handled above.
        val out = StringBuilder()
        var i = 0
        val n = w.length
        while (i < n) {
            val c = w[i]
            when {
                c == NUKTA -> {}
                c in consonants -> {
                    out.append(consonants.getValue(c))
                    val next = w.getOrNull(i + 1)
                    when {
                        next == null -> {}
                        next in vowelSigns -> {}
                        next == HASANTA -> {}
                        next in consonants -> {
                            val afterNext = w.getOrNull(i + 2)
                            val initial = i == 0
                            val nextHasVowel = afterNext != null && afterNext in vowelSigns
                            if (initial || !nextHasVowel) {
                                if (initial || afterNext != null) out.append('o')
                            }
                        }
                        else -> {}
                    }
                }
                c in vowelSigns -> out.append(vowelSigns.getValue(c))
                c in independentVowels -> out.append(independentVowels.getValue(c))
                c == HASANTA -> {}
                c == 'ং' -> out.append("ng")
                c == 'ঃ' -> out.append("h")
                c == 'ঁ' -> {}
                isBangla(c) -> {}
                else -> out.append(c.lowercaseChar())
            }
            i++
        }
        return out.toString()
    }

    fun sentence(s: String): String = s.split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") { word(it) }
}
