package com.pharmabill.app.domain.usecase

import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.domain.model.BillStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks which open draft receives photo, voice and search additions. */
@Singleton
class DraftManager @Inject constructor(
    private val settings: SettingsStore,
    private val bills: BillRepository,
) {
    val activeDraftId: Flow<Long?> = settings.settings.map { it.activeDraftId }

    /** Returns the active draft, creating Draft #N if there is none. */
    suspend fun ensureActive(): Long {
        val id = settings.current().activeDraftId
        if (id != null) {
            val b = bills.bill(id)
            if (b != null && b.status == BillStatus.DRAFT) return id
        }
        return newDraft()
    }

    suspend fun newDraft(label: String? = null): Long {
        val id = bills.createDraft(label)
        settings.setActiveDraft(id)
        return id
    }

    suspend fun setActive(id: Long?) = settings.setActiveDraft(id)
}
