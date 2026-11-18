package com.pharmabill.app

import com.pharmabill.app.ai.match.MatchEntry
import com.pharmabill.app.ai.match.MedicineMatcher
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind

object TestData {
    val napa = MedicineInfo(1, "Napa", "500 mg", MedicineKind.STRIP, 12, 10, 1200, 0)
    val seclo = MedicineInfo(2, "Seclo", "20 mg", MedicineKind.STRIP, 10, 3, 6000, 0)
    val ace = MedicineInfo(3, "Ace", "500 mg", MedicineKind.STRIP, 10, 20, 1200, 0)
    val napaSyrup = MedicineInfo(4, "Napa", "120 mg/5 ml", MedicineKind.UNIT, 1, 1, 0, 3500)
    val napaExtra = MedicineInfo(5, "Napa Extra", "500 mg+65 mg", MedicineKind.STRIP, 10, 20, 2500, 0)
    val seclo40 = MedicineInfo(6, "Seclo", "40 mg", MedicineKind.STRIP, 10, 3, 9000, 0)
    val fexo = MedicineInfo(7, "Fexo", "120 mg", MedicineKind.STRIP, 10, 5, 9000, 0)
    val ceftron = MedicineInfo(8, "Ceftron", "1 g", MedicineKind.UNIT, 1, 1, 0, 21000)

    val infos = listOf(napa, seclo, ace, napaSyrup, napaExtra, seclo40, fexo, ceftron).associateBy { it.id }

    val entries = listOf(
        MatchEntry(1, "Napa", "500 mg", "Paracetamol", "Beximco", MedicineKind.STRIP, "নাপা", listOf("nappa")),
        MatchEntry(2, "Seclo", "20 mg", "Omeprazole", "Square", MedicineKind.STRIP, "সেকলো", listOf("seklo")),
        MatchEntry(3, "Ace", "500 mg", "Paracetamol", "Square", MedicineKind.STRIP, "এইস", listOf("ace", "es")),
        MatchEntry(4, "Napa", "120 mg/5 ml", "Paracetamol", "Beximco", MedicineKind.UNIT, "নাপা", emptyList()),
        MatchEntry(5, "Napa Extra", "500 mg+65 mg", "Paracetamol + Caffeine", "Beximco", MedicineKind.STRIP, "নাপা এক্সট্রা", emptyList()),
        MatchEntry(6, "Seclo", "40 mg", "Omeprazole", "Square", MedicineKind.STRIP, "সেকলো", emptyList()),
        MatchEntry(7, "Fexo", "120 mg", "Fexofenadine", "Square", MedicineKind.STRIP, "ফেক্সো", emptyList()),
        MatchEntry(8, "Ceftron", "1 g", "Ceftriaxone", "Square", MedicineKind.UNIT, "সেফট্রন", emptyList()),
    )

    fun matcher() = MedicineMatcher(entries)
}
