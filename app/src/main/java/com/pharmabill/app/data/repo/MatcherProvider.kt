package com.pharmabill.app.data.repo

import com.pharmabill.app.ai.match.MatchEntry
import com.pharmabill.app.ai.match.MedicineMatcher
import com.pharmabill.app.data.db.dao.AliasDao
import com.pharmabill.app.data.db.dao.MedicineDao
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.di.AppScope
import com.pharmabill.app.domain.model.MedicineInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps an in-memory fuzzy index of every medicine and alias, rebuilt whenever the tables change. */
@Singleton
class MatcherProvider @Inject constructor(
    medicineDao: MedicineDao,
    aliasDao: AliasDao,
    @AppScope scope: CoroutineScope,
) {
    data class Snapshot(val matcher: MedicineMatcher, val medicines: Map<Long, MedicineEntity>)

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    init {
        scope.launch {
            combine(medicineDao.observeAll(), aliasDao.observeAll()) { meds, aliases ->
                val byMed = aliases.groupBy { it.medicineId }
                val entries = meds.map { m ->
                    MatchEntry(
                        id = m.id, brand = m.brand, strength = m.strength, generic = m.generic, company = m.company,
                        kind = m.kind, banglaName = m.banglaName, aliases = byMed[m.id].orEmpty().map { it.alias },
                    )
                }
                Snapshot(MedicineMatcher(entries), meds.associateBy { it.id })
            }.collect { _snapshot.value = it }
        }
    }

    suspend fun current(): Snapshot = snapshot.filterNotNull().first()

    fun infoOf(id: Long): MedicineInfo? = _snapshot.value?.medicines?.get(id)?.toInfo()

    fun medicine(id: Long): MedicineEntity? = _snapshot.value?.medicines?.get(id)
}
