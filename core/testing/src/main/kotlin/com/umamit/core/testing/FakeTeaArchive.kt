package com.umamit.core.testing

import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.TeaArchive
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [TeaArchive] on [FakeSessions] and its collection, as on Room: the active brew session's tea
 * (from the active brew's session row) can't be archived; [archived] records every archive.
 */
class FakeTeaArchive(private val s: FakeSessions) : TeaArchive {
    val archived = mutableListOf<String>()

    override fun observeActiveTeaId(): Flow<String?> = combine(s.activeRows, s.sessionRows) { active, sessions ->
        active[TimerSlot.BREW]?.activeBrew?.sessionId?.let { sessions[it] }?.snapshot?.teaId
    }.distinctUntilChanged()

    override suspend fun archive(teaId: String): ArchiveResult {
        val active = s.activeRows.value[TimerSlot.BREW]?.activeBrew?.sessionId
            ?.let { s.sessionRows.value[it] }?.snapshot?.teaId
        val tea = s.collection.teas.getTea(teaId)
        return when {
            active == teaId -> ArchiveResult.InActiveSession

            tea == null -> ArchiveResult.NotFound

            else -> {
                s.collection.teas.saveTea(tea.copy(isArchived = true))
                archived += teaId
                ArchiveResult.Archived
            }
        }
    }

    override suspend fun restore(teaId: String) {
        s.collection.teas.getTea(teaId)?.let { s.collection.teas.saveTea(it.copy(isArchived = false)) }
    }
}
