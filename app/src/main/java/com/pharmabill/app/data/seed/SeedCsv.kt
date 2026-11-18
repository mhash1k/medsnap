package com.pharmabill.app.data.seed

import com.pharmabill.app.domain.model.MedicineForm
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.util.Fmt

data class SeedRow(
    val brand: String,
    val generic: String,
    val strength: String,
    val form: MedicineForm,
    val kind: MedicineKind,
    val company: String,
    val piecesPerStrip: Int,
    val stripsPerBox: Int,
    val stripPrice: Long,
    val unitPrice: Long,
    val banglaName: String?,
    val voiceAliases: List<String>,
    val priceVerified: Boolean,
)

/** Small CSV reader for the bundled seed file (supports quoted fields). */
object SeedCsv {
    fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out.map { it.trim() }
    }

    fun parse(lines: List<String>): List<SeedRow> {
        val nonEmpty = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        if (nonEmpty.isEmpty()) return emptyList()
        val header = splitLine(nonEmpty.first()).map { it.lowercase() }
        fun idx(name: String) = header.indexOf(name)
        return nonEmpty.drop(1).mapNotNull { line ->
            val f = splitLine(line)
            fun get(name: String): String = idx(name).let { if (it >= 0) f.getOrNull(it).orEmpty() else "" }
            val brand = get("brand").ifBlank { return@mapNotNull null }
            val form = MedicineForm.parse(get("form"))
            val kind = when (get("kind").uppercase()) {
                "STRIP" -> MedicineKind.STRIP
                "UNIT" -> MedicineKind.UNIT
                else -> form.defaultKind
            }
            SeedRow(
                brand = brand,
                generic = get("generic"),
                strength = get("strength"),
                form = form,
                kind = kind,
                company = get("company"),
                piecesPerStrip = get("pieces_per_strip").toIntOrNull() ?: if (kind == MedicineKind.STRIP) 10 else 1,
                stripsPerBox = get("strips_per_box").toIntOrNull() ?: 1,
                stripPrice = Fmt.parseTaka(get("strip_price")) ?: 0,
                unitPrice = Fmt.parseTaka(get("unit_price")) ?: 0,
                banglaName = get("bangla_name").ifBlank { null },
                voiceAliases = get("voice_aliases").split('|').map { it.trim() }.filter { it.isNotEmpty() },
                priceVerified = get("price_verified").equals("true", ignoreCase = true),
            )
        }
    }
}
