package com.pharmabill.app.ai.match

import com.pharmabill.app.ai.ocr.TextNormalizer
import com.pharmabill.app.domain.model.MedicineKind

data class MatchEntry(
    val id: Long,
    val brand: String,
    val strength: String,
    val generic: String,
    val company: String,
    val kind: MedicineKind,
    val banglaName: String? = null,
    val aliases: List<String> = emptyList(),
) {
    internal val brandKey: String = TextNormalizer.brandTokens(brand).joinToString(" ")
    internal val brandLower: String = brand.lowercase().replace('-', ' ')
    internal val strengthKey: String? = TextNormalizer.firstStrengthKey(strength)
    internal val strengthNumber: String? = TextNormalizer.strengths(strength).firstOrNull()?.value
    internal val aliasKeys: List<String> = aliases.map { TextNormalizer.brandTokens(it).joinToString(" ") }.filter { it.isNotEmpty() }
    internal val aliasLower: List<String> = aliases.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
    internal val phonetic: String = PhoneticKey.of(brandLower)
    internal val companyWord: String? = TextNormalizer.brandTokens(company).firstOrNull()
    internal val genericWord: String? = TextNormalizer.brandTokens(generic).firstOrNull()
    internal val brandTokenList: List<String> = brandKey.split(' ').filter { it.isNotEmpty() }
    internal val aliasTokenLists: List<List<String>> = aliasKeys.map { k -> k.split(' ').filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
}

data class MatchCandidate(val id: Long, val score: Double)

/**
 * In-memory fuzzy index over all medicines.
 * OCR score = 0.6 x brand similarity (Jaro-Winkler) + 0.3 x strength match + 0.1 x company/generic match.
 * When the text has no strength at all the strength weight is dropped and the rest is rescaled.
 */
class MedicineMatcher(entries: List<MatchEntry>) {

    private val all: List<MatchEntry> = entries
    private val byId: Map<Long, MatchEntry> = entries.associateBy { it.id }
    private val byFirstLetter: Map<Char, List<MatchEntry>> = buildMap<Char, MutableList<MatchEntry>> {
        for (e in entries) {
            val keys = (listOf(e.brandKey) + e.aliasKeys).mapNotNull { it.firstOrNull() }.toSet()
            for (k in keys) getOrPut(k) { mutableListOf() }.add(e)
        }
    }

    val size: Int get() = all.size

    fun entry(id: Long): MatchEntry? = byId[id]

    fun entriesWithBrand(brandLower: String): List<MatchEntry> = all.filter { it.brandLower == brandLower }

    // ---------------------------------------------------------------- OCR

    fun matchOcrBlock(blockText: String, fullText: String = blockText, limit: Int = 3): List<MatchCandidate> {
        val tokens = TextNormalizer.brandTokens(blockText)
        if (tokens.isEmpty()) return emptyList()
        val windows = windows(tokens, 3)
        val textStrengths = TextNormalizer.strengths(blockText).map { it.key }.toSet()
        val fullTokens = TextNormalizer.brandTokens(fullText).toSet()

        val pool = LinkedHashSet<MatchEntry>()
        for (w in windows) {
            val first = w.first()
            byFirstLetter[first]?.let { pool.addAll(it) }
            ocrSwapLetters(first).forEach { c -> byFirstLetter[c]?.let { pool.addAll(it) } }
        }
        if (pool.isEmpty() && all.size <= 5000) pool.addAll(all)

        val scored = pool.mapNotNull { e ->
            val brandSim = (listOf(e.brandKey) + e.aliasKeys).maxOf { key -> windows.maxOf { JaroWinkler.similarity(it, key) } }
            if (brandSim < 0.70) return@mapNotNull null
            val company = if ((e.companyWord != null && e.companyWord in fullTokens) ||
                (e.genericWord != null && e.genericWord in fullTokens)
            ) 1.0 else 0.0
            val score = if (textStrengths.isEmpty()) {
                (0.6 * brandSim + 0.1 * company) / 0.7
            } else {
                val strength = if (e.strengthKey != null && e.strengthKey in textStrengths) 1.0 else 0.0
                0.6 * brandSim + 0.3 * strength + 0.1 * company
            }
            MatchCandidate(e.id, score.coerceIn(0.0, 1.0))
        }
        return topDistinct(scored, limit)
    }

    /**
     * Whole-photo identification: decides which ONE medicine the photo shows.
     *
     * Every printed line is read together. A medicine only qualifies when every word of its brand
     * matches a run of words on the pack ("Napa Rapid" needs both NAPA and RAPID), so side text such
     * as "Actizorb technology", "Beximco Pharma" or licence numbers can no longer become medicines.
     * Among qualifying brands the most specific wins (Napa Rapid beats Napa), a brand that is only the
     * generic name of a stronger match is dropped, and company / generic / strength are supporting
     * evidence. Returns at most [limit] candidates, best first; an empty list means "not recognised".
     */
    fun identify(lines: List<String>, limit: Int = 3): List<MatchCandidate> =
        identifyWith(lines, limit, BRAND_MIN, 0.8, 1.0).ifEmpty {
            // Nothing clear: try once more with loose matching, capped below green so staff pick from the options.
            identifyWith(lines, limit, BRAND_LOOSE, 0.7, LOOSE_CAP)
        }

    private fun identifyWith(lines: List<String>, limit: Int, brandMin: Double, wordMin: Double, cap: Double): List<MatchCandidate> {
        val lineTokens = lines.map { TextNormalizer.brandTokens(it) }.filter { it.isNotEmpty() }
        if (lineTokens.isEmpty()) return emptyList()
        val allTokens = lineTokens.flatten().toSet()
        val fullText = lines.joinToString(" ")
        val textStrengths = TextNormalizer.strengths(fullText).map { it.key }.toSet()
        val textStrengthNumbers = TextNormalizer.strengths(fullText).map { it.value }.toSet()
        val knownCompanies = companyWords
        val textCompanies = allTokens.filter { it in knownCompanies }.toSet()

        val pool = LinkedHashSet<MatchEntry>()
        for (t in allTokens) {
            byFirstLetter[t.first()]?.let { pool.addAll(it) }
            ocrSwapLetters(t.first()).forEach { c -> byFirstLetter[c]?.let { pool.addAll(it) } }
        }

        data class Hit(val e: MatchEntry, val brandSim: Double, val repeats: Int, val tokens: List<String>)
        val hits = pool.mapNotNull { e ->
            var best = 0.0
            var bestTokens = e.brandTokenList
            var repeats = 0
            for (brand in listOf(e.brandTokenList) + e.aliasTokenLists) {
                if (brand.isEmpty()) continue
                var lineHits = 0
                for (lt in lineTokens) {
                    val sim = phraseSimilarity(brand, lt, wordMin)
                    if (sim >= brandMin) lineHits++
                    if (sim > best) { best = sim; bestTokens = brand }
                }
                repeats = maxOf(repeats, lineHits)
            }
            if (best < brandMin) null else Hit(e, best, repeats, bestTokens)
        }
        if (hits.isEmpty()) return emptyList()

        // Most specific brand wins: drop "Napa" when "Napa Rapid" matched just as well.
        val specific = hits.filter { h ->
            hits.none { o ->
                o.tokens.size > h.tokens.size && o.brandSim >= BRAND_STRONG &&
                    o.brandSim >= h.brandSim - 0.03 && o.tokens.containsAll(h.tokens)
            }
        }
        // A brand that is only the generic of a stronger match ("Paracetamol" on a Napa Rapid strip) is not the medicine.
        val strongGenerics = specific.filter { it.brandSim >= BRAND_STRONG && it.e.genericWord != null && it.e.brandKey != it.e.genericWord }
            .mapNotNull { it.e.genericWord }.toSet()
        val kept = specific.filterNot { h -> h.e.brandKey == h.e.genericWord && h.e.brandKey in strongGenerics }

        val scored = kept.map { h ->
            val e = h.e
            val strength = when {
                textStrengths.isEmpty() || e.strengthKey == null -> 0.5
                e.strengthKey in textStrengths -> 1.0
                e.strengthNumber != null && e.strengthNumber in textStrengthNumbers -> 0.8
                else -> 0.0
            }
            val companyOk = e.companyWord != null && e.companyWord in textCompanies
            val companyClash = !companyOk && textCompanies.isNotEmpty() && e.companyWord != null && e.companyWord in knownCompanies
            val genericOk = e.genericWord != null && e.brandKey != e.genericWord && allTokens.any { JaroWinkler.similarity(it, e.genericWord) >= 0.9 }
            val support = (if (companyOk) 0.5 else 0.0) + (if (genericOk) 0.5 else 0.0) - (if (companyClash) 0.5 else 0.0)
            val repeat = (h.repeats - 1).coerceIn(0, 3) * 0.01
            val score = 0.65 * h.brandSim + 0.25 * strength + 0.10 * support.coerceIn(0.0, 1.0) + repeat - (if (companyClash) 0.05 else 0.0)
            MatchCandidate(e.id, score.coerceIn(0.0, cap))
        }
        val ranked = topDistinct(scored, scored.size)
        val top = ranked.firstOrNull() ?: return emptyList()
        return ranked.filter { it.score >= top.score - KEEP_WITHIN && it.score >= 0.5 }.take(limit)
    }

    /** Company first words seen in the catalogue (BEXIMCO, SQUARE, ...), used to spot the maker on the pack. */
    private val companyWords: Set<String> = all.mapNotNull { it.companyWord }.filter { it.length >= 3 }.toSet()

    private fun ocrSwapLetters(c: Char): List<Char> = when (c) {
        'O' -> listOf('0'); 'I' -> listOf('L', '1'); 'L' -> listOf('I'); 'S' -> listOf('5'); else -> emptyList()
    }

    // ---------------------------------------------------------------- Voice

    /**
     * Matches a spoken span (already lowercased Latin, optionally with the original Bangla text).
     * Score is the mean of Jaro-Winkler and normalised edit similarity (JW alone is too lenient on
     * short words), raised to [PHONETIC_SCORE] when phonetic keys agree so it still needs a tap.
     */
    fun matchSpoken(span: String, original: String? = null, kindHint: MedicineKind? = null, limit: Int = 3): List<MatchCandidate> {
        val s = span.lowercase().trim()
        if (s.isEmpty()) return emptyList()
        val key = PhoneticKey.of(s)
        val scored = all.map { e ->
            var sim = spokenSimilarity(s, e.brandLower)
            for (a in e.aliasLower) sim = maxOf(sim, spokenSimilarity(s, a))
            if (key.length >= 2 && key == e.phonetic && sim > 0.6) sim = maxOf(sim, PHONETIC_SCORE)
            if (original != null && e.banglaName != null && original.isNotBlank()) {
                if (original.trim() == e.banglaName.trim()) sim = 1.0
                else sim = maxOf(sim, JaroWinkler.similarity(original.trim(), e.banglaName.trim()) - 0.03)
            }
            e to sim
        }
        val filtered = if (kindHint != null && scored.any { it.first.kind == kindHint && it.second >= 0.6 }) {
            scored.filter { it.first.kind == kindHint }
        } else scored
        return topDistinct(filtered.filter { it.second >= 0.5 }.map { MatchCandidate(it.first.id, it.second) }, limit)
    }

    // ---------------------------------------------------------------- Search

    /** Brand, generic, Bangla name and alias search for the manual add bar. */
    fun search(query: String, limit: Int = 30): List<Long> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val ranked = all.mapNotNull { e ->
            val brand = e.brand.lowercase()
            val generic = e.generic.lowercase()
            val rank = when {
                brand.startsWith(q) -> 0
                brand.contains(q) -> 1
                generic.startsWith(q) -> 2
                generic.contains(q) -> 3
                e.banglaName?.contains(query.trim()) == true -> 2
                e.aliasLower.any { it.startsWith(q) } -> 2
                else -> null
            }
            rank?.let { e to it }
        }.sortedWith(compareBy({ it.second }, { it.first.brand.length }, { it.first.brand }))
        if (ranked.isNotEmpty()) return ranked.take(limit).map { it.first.id }
        return all.map { it to JaroWinkler.similarity(q, it.brandLower) }
            .filter { it.second >= 0.80 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.id }
    }

    companion object {
        /** Phonetic-only matches stay below the green threshold so staff confirm them. */
        const val PHONETIC_SCORE = 0.84

        fun spokenSimilarity(a: String, b: String): Double {
            if (a == b) return 1.0
            val maxLen = maxOf(a.length, b.length).coerceAtLeast(1)
            val lev = 1.0 - Levenshtein.distance(a, b).toDouble() / maxLen
            return (JaroWinkler.similarity(a, b) + lev) / 2.0
        }

        /** Below this a brand is not considered present on the pack at all. */
        const val BRAND_MIN = 0.86
        const val BRAND_STRONG = 0.92
        const val BRAND_LOOSE = 0.76
        /** Loose matches never reach the green threshold. */
        const val LOOSE_CAP = 0.80
        /** Alternatives offered to staff must be this close to the best guess. */
        const val KEEP_WITHIN = 0.15

        /** Similarity of one OCR word to one brand word: Jaro-Winkler and edit distance must both agree. */
        fun wordSimilarity(a: String, b: String): Double {
            if (a == b) return 1.0
            val maxLen = maxOf(a.length, b.length)
            val minLen = minOf(a.length, b.length)
            if (minLen == 0) return 0.0
            // Very short words need an exact match ("ACE" must not match "ACTIZORB"); 3 letters may stand for a 4-letter brand.
            if (minLen < 3 || (minLen == 3 && maxLen > 4)) return 0.0
            if (maxLen - minLen > maxOf(2, maxLen / 4)) return 0.0
            val lev = 1.0 - Levenshtein.distance(a, b).toDouble() / maxLen
            return minOf(JaroWinkler.similarity(a, b), (JaroWinkler.similarity(a, b) + lev) / 2.0)
        }

        /** Best match of a multi-word brand against consecutive words of one printed line; every word must match. */
        fun phraseSimilarity(brand: List<String>, line: List<String>, wordMin: Double = 0.8): Double {
            if (brand.isEmpty() || line.size < brand.size) return 0.0
            var best = 0.0
            for (i in 0..line.size - brand.size) {
                var sum = 0.0
                var min = 1.0
                for (k in brand.indices) {
                    val s = wordSimilarity(line[i + k], brand[k])
                    sum += s
                    if (s < min) min = s
                }
                if (min < wordMin) continue
                best = maxOf(best, sum / brand.size)
            }
            return best
        }

        fun windows(tokens: List<String>, maxLen: Int): List<String> {
            val out = mutableListOf<String>()
            for (i in tokens.indices) {
                for (len in 1..maxLen) {
                    if (i + len > tokens.size) break
                    out += tokens.subList(i, i + len).joinToString(" ")
                }
            }
            return out
        }

        fun topDistinct(list: List<MatchCandidate>, limit: Int): List<MatchCandidate> =
            list.groupBy { it.id }.map { (_, v) -> v.maxBy { it.score } }
                .sortedWith(compareByDescending<MatchCandidate> { it.score }.thenBy { it.id })
                .take(limit)
    }
}
