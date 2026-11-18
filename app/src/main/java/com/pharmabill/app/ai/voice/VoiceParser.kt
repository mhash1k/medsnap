package com.pharmabill.app.ai.voice

import com.pharmabill.app.ai.match.MatchCandidate
import com.pharmabill.app.ai.match.MedicineMatcher
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.QuantityResolver

enum class VoiceIntent { ADD, REMOVE, CHANGE, STOCK, PRICE, FINISH, NEW_DRAFT }

data class VoiceItem(
    val medicineId: Long,
    val candidates: List<MatchCandidate>,
    val unit: SaleUnit,
    val quantity: Int,
    val flag: Flag,
    val score: Double,
    val heard: String,
)

sealed interface VoiceCommand {
    data class AddItems(val items: List<VoiceItem>) : VoiceCommand
    data class RemoveItem(val item: VoiceItem) : VoiceCommand
    data class ChangeQuantity(val item: VoiceItem) : VoiceCommand
    data class StockQuery(val item: VoiceItem) : VoiceCommand
    data class PriceQuery(val item: VoiceItem) : VoiceCommand
    data object FinishBill : VoiceCommand
    data object NewDraft : VoiceCommand
    data class NotUnderstood(val text: String) : VoiceCommand
}

data class VoiceParse(val command: VoiceCommand, val score: Double, val text: String)

/**
 * Rule-based parser for mixed Bangla-English pharmacy commands.
 * Steps: clean -> intent -> split items -> find medicine span -> quantity/unit -> flags.
 */
class VoiceParser(
    private val matcher: MedicineMatcher,
    private val infoOf: (Long) -> MedicineInfo?,
    private val flags: FlagEvaluator = FlagEvaluator(),
) {
    internal data class Tok(val latin: String, val raw: String)

    private enum class UnitWord { PIECE, STRIP, BOX, UNIT }

    fun parseBest(alternatives: List<String>): VoiceParse {
        val parsed = alternatives.filter { it.isNotBlank() }.map { parse(it) }
        if (parsed.isEmpty()) return VoiceParse(VoiceCommand.NotUnderstood(""), 0.0, "")
        return parsed.withIndex().maxWith(compareBy<IndexedValue<VoiceParse>> { it.value.score }.thenByDescending { it.index }).value
    }

    fun parse(text: String): VoiceParse {
        val tokens = clean(text)
        val latin = tokens.joinToString(" ") { it.latin }
        val intent = detectIntent(tokens.map { it.latin }, latin)
        when (intent) {
            VoiceIntent.FINISH -> return VoiceParse(VoiceCommand.FinishBill, 1.0, text)
            VoiceIntent.NEW_DRAFT -> return VoiceParse(VoiceCommand.NewDraft, 1.0, text)
            else -> {}
        }
        val content = tokens.filter { it.latin !in intentWords.getValue(intent) && it.latin !in fillers }
        val segments = split(content)
        val items = segments.mapNotNull { parseSegment(it) }
        if (items.isEmpty()) return VoiceParse(VoiceCommand.NotUnderstood(text), 0.0, text)
        val first = items.first()
        val cmd = when (intent) {
            VoiceIntent.ADD -> VoiceCommand.AddItems(items)
            VoiceIntent.REMOVE -> VoiceCommand.RemoveItem(first)
            VoiceIntent.CHANGE -> VoiceCommand.ChangeQuantity(first)
            VoiceIntent.STOCK -> VoiceCommand.StockQuery(first)
            VoiceIntent.PRICE -> VoiceCommand.PriceQuery(first)
            else -> VoiceCommand.NotUnderstood(text)
        }
        val score = if (intent == VoiceIntent.ADD) items.sumOf { it.score } else first.score
        return VoiceParse(cmd, score, text)
    }

    // ------------------------------------------------------------------ cleaning

    internal fun clean(text: String): List<Tok> {
        val prepared = BanglaTransliterator.convertDigits(text)
            .replace(Regex("""[,،।;]"""), " , ")
            .replace(Regex("""[?!.:"'()]"""), " ")
        return prepared.split(Regex("""\s+""")).filter { it.isNotEmpty() }.flatMap { raw ->
            val latin = BanglaTransliterator.word(raw).lowercase()
            // "2ta" / "2টা" -> "2" "ta"
            val m = Regex("""^(\d+)([a-z]+)$""").find(latin)
            if (m != null && m.groupValues[2] in numberSuffixes) listOf(Tok(m.groupValues[1], raw), Tok(m.groupValues[2], raw))
            else listOf(Tok(latin, raw))
        }
    }

    internal fun detectIntent(words: List<String>, joined: String): VoiceIntent {
        val set = words.toSet()
        if (joined.contains("notun bill") || joined.contains("new bill") || joined.contains("notun draft")) return VoiceIntent.NEW_DRAFT
        if (joined.contains("bill koro") || joined.contains("bill kor") || joined.contains("bill confirm") ||
            "confirm" in set || "shesh" in set || "sesh" in set || "finish" in set
        ) return VoiceIntent.FINISH
        if ("stock" in set || "stok" in set || joined.contains("koto ache") || joined.contains("koyta ache") ||
            joined.contains("koita ache") || joined.contains("koyti ache")
        ) return VoiceIntent.STOCK
        if ("dam" in set || "daam" in set || "price" in set || joined.contains("koto taka")) return VoiceIntent.PRICE
        if (set.any { it in intentWords.getValue(VoiceIntent.REMOVE) }) return VoiceIntent.REMOVE
        if (set.any { it in intentWords.getValue(VoiceIntent.CHANGE) }) return VoiceIntent.CHANGE
        return VoiceIntent.ADD
    }

    private fun split(tokens: List<Tok>): List<List<Tok>> {
        val out = mutableListOf<MutableList<Tok>>(mutableListOf())
        for (t in tokens) {
            if (t.latin in separators) {
                if (out.last().isNotEmpty()) out += mutableListOf<Tok>()
            } else out.last() += t
        }
        return out.filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ segments

    private fun parseSegment(seg: List<Tok>): VoiceItem? {
        val unitWord = seg.firstNotNullOfOrNull { unitOf(it.latin) }
        val explicitUnitIsPieceWord = seg.any { it.latin in pieceWords && it.latin !in numberSuffixes }
        val kindHint = when (unitWord) {
            UnitWord.UNIT -> MedicineKind.UNIT
            UnitWord.STRIP, UnitWord.BOX -> MedicineKind.STRIP
            UnitWord.PIECE -> if (explicitUnitIsPieceWord) MedicineKind.STRIP else null
            null -> null
        }
        val isQty = seg.map { numberOf(it.latin) != null || unitOf(it.latin) != null || it.latin in halfWords }

        // Longest 1-3 word span of non-quantity tokens with the best score.
        var best: Triple<Int, Int, List<MatchCandidate>>? = null
        for (i in seg.indices) {
            if (isQty[i]) continue
            for (len in 1..3) {
                if (i + len > seg.size) break
                if ((i until i + len).any { isQty[it] }) break
                val span = seg.subList(i, i + len)
                val cands = matcher.matchSpoken(
                    span.joinToString(" ") { it.latin },
                    span.joinToString(" ") { it.raw }.takeIf { BanglaTransliterator.containsBangla(it) },
                    kindHint,
                )
                val top = cands.firstOrNull() ?: continue
                val cur = best
                if (cur == null || top.score > cur.third.first().score + 1e-9 ||
                    (kotlin.math.abs(top.score - cur.third.first().score) < 1e-9 && len > cur.second)
                ) best = Triple(i, len, cands)
            }
        }
        val (start, len, rawCands) = best ?: return null
        if (rawCands.first().score < MIN_SCORE) return null
        var cands = rawCands
        val used = (start until start + len).toMutableSet()

        // Optional strength right after the name: "Napa 500".
        val nextIdx = start + len
        val nextNum = seg.getOrNull(nextIdx)?.latin?.takeIf { it.all(Char::isDigit) }
        if (nextNum != null) {
            val brand = matcher.entry(cands.first().id)?.brand?.lowercase()?.replace('-', ' ')
            val sameBrand = brand?.let { matcher.entriesWithBrand(it) }.orEmpty()
            val withStrength = sameBrand.firstOrNull { it.strengthNumber == nextNum }
            if (withStrength != null) {
                val topScore = cands.first().score
                cands = listOf(MatchCandidate(withStrength.id, topScore)) +
                    cands.filter { it.id != withStrength.id }.map { it.copy(score = minOf(it.score, topScore - 0.1)) }
                used += nextIdx
            }
        }

        val chosen = cands.first()
        val info = infoOf(chosen.id) ?: return null

        var number: Int? = null
        var suffixPiece = false
        var half = false
        seg.forEachIndexed { idx, t ->
            if (idx in used) return@forEachIndexed
            if (t.latin in halfWords) half = true
            if (number == null) {
                numberOf(t.latin)?.let { (n, pieceSuffix) ->
                    number = n
                    suffixPiece = pieceSuffix
                }
            }
        }
        val (unit, qty) = resolveQuantity(info, unitWord, number, half, suffixPiece)
        val second = cands.getOrNull(1)?.score
        val flag = if (flags.nameFlag(chosen.score, second) == Flag.GREEN) Flag.GREEN else Flag.YELLOW
        val heard = seg.joinToString(" ") { it.raw }
        return VoiceItem(chosen.id, cands, unit, qty, flag, chosen.score, heard)
    }

    private fun resolveQuantity(info: MedicineInfo, unitWord: UnitWord?, number: Int?, half: Boolean, suffixPiece: Boolean): Pair<SaleUnit, Int> {
        val n = number ?: 1
        if (info.kind == MedicineKind.UNIT) return SaleUnit.UNIT to n.coerceAtLeast(1)
        if (half && (unitWord == null || unitWord == UnitWord.STRIP)) {
            return SaleUnit.PIECE to QuantityResolver.halfStripPieces(info) * (number ?: 1)
        }
        return when (unitWord) {
            UnitWord.STRIP -> SaleUnit.STRIP to n
            UnitWord.BOX -> SaleUnit.BOX to n
            UnitWord.PIECE, UnitWord.UNIT, null -> {
                @Suppress("UNUSED_VARIABLE") val ignored = suffixPiece
                SaleUnit.PIECE to n
            }
        }.let { (u, q) -> u to q.coerceAtLeast(1) }
    }

    private fun unitOf(w: String): UnitWord? = when (w) {
        in pieceWords -> UnitWord.PIECE
        in stripWords -> UnitWord.STRIP
        in boxWords -> UnitWord.BOX
        in unitWords -> UnitWord.UNIT
        else -> null
    }

    /** Returns the number and whether a piece suffix (ta/ti/to) was attached. */
    internal fun numberOf(w: String): Pair<Int, Boolean>? {
        w.toIntOrNull()?.let { return it to false }
        numberWords[w]?.let { return it to false }
        for (suffix in numberSuffixes) {
            if (w.length > suffix.length && w.endsWith(suffix)) {
                val stem = w.dropLast(suffix.length)
                (numberWords[stem] ?: stem.toIntOrNull())?.let { return it to true }
            }
        }
        // Doubled consonant before the suffix: "shatta", "aatta".
        if (w.length > 3 && w.endsWith("ta") && w[w.length - 3] == 't') {
            numberWords[w.dropLast(3)]?.let { return it to true }
            numberWords[w.dropLast(2)]?.let { return it to true }
        }
        return null
    }

    companion object {
        const val MIN_SCORE = 0.72

        val numberWords: Map<String, Int> = mapOf(
            "ek" to 1, "one" to 1, "dui" to 2, "du" to 2, "two" to 2, "tin" to 3, "three" to 3,
            "char" to 4, "four" to 4, "pach" to 5, "panch" to 5, "five" to 5,
            "choy" to 6, "chhoy" to 6, "six" to 6, "shat" to 7, "sat" to 7, "saat" to 7, "seven" to 7,
            "aat" to 8, "aath" to 8, "eight" to 8, "noy" to 9, "nine" to 9,
            "dosh" to 10, "dos" to 10, "ten" to 10, "egaro" to 11, "eleven" to 11, "baro" to 12, "twelve" to 12,
            "tero" to 13, "thirteen" to 13, "choddo" to 14, "fourteen" to 14, "ponero" to 15, "fifteen" to 15,
            "sholo" to 16, "sixteen" to 16, "shotero" to 17, "sotero" to 17, "seventeen" to 17,
            "atharo" to 18, "eighteen" to 18, "unish" to 19, "nineteen" to 19, "bish" to 20, "twenty" to 20,
            "tirish" to 30, "thirty" to 30,
        )
        val numberSuffixes = listOf("ta", "ti", "to", "te")
        val pieceWords = setOf("ta", "ti", "pis", "piece", "pieces", "pcs", "pc", "tablet", "tablets", "tab", "capsule", "capsules", "cap")
        val stripWords = setOf("pata", "patta", "strip", "strips", "pata gula")
        val boxWords = setOf("box", "boxes", "bakso", "baksho", "packet", "paket")
        val unitWords = setOf("botol", "bottle", "bottles", "shishi", "vial", "tube", "unit", "units")
        val halfWords = setOf("adha", "half", "ardhek")
        val separators = setOf("ar", "aar", "and", "o", ",", "plus")
        val fillers = setOf(
            "amake", "den", "din", "dao", "dilen", "please", "plz", "ekhane", "bhai", "apa", "ki", "er", "r",
            "aro", "give", "me", "add", "nao", "chai", "lagbe", "dorkar",
        )

        private val intentWords: Map<VoiceIntent, Set<String>> = mapOf(
            VoiceIntent.ADD to emptySet(),
            VoiceIntent.REMOVE to setOf("bad", "baad", "kete", "remove", "delete", "bado", "bade", "cancel"),
            VoiceIntent.CHANGE to setOf("koro", "kor", "hobe", "change", "korun", "koren", "kore", "set"),
            VoiceIntent.STOCK to setOf("stock", "stok", "koto", "ache", "koyta", "koita", "koyti"),
            VoiceIntent.PRICE to setOf("dam", "daam", "price", "koto", "taka"),
            VoiceIntent.FINISH to emptySet(),
            VoiceIntent.NEW_DRAFT to emptySet(),
        )
    }
}
