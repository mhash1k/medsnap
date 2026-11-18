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
