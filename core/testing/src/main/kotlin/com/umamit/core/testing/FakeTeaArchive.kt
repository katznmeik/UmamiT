package com.umamit.core.testing

import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.DeleteResult
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.VesselArchive
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [TeaArchive] on [FakeSessions] and its collection, as on Room: the active brew session's tea
 * (from the active brew's session row) can be neither archived nor deleted; [archived] records every
 * archive and [deleted] every delete. A delete cascades in the collection and keeps the tea's sessions
 * with their name, without the tea and its recipes (05 §3.1).
 */
class FakeTeaArchive(private val s: FakeSessions) : TeaArchive {
    val archived = mutableListOf<String>()

    val deleted = mutableListOf<String>()

    override fun observeActiveTeaId(): Flow<String?> = combine(s.activeRows, s.sessionRows) { active, sessions ->
        active[TimerSlot.BREW]?.activeBrew?.sessionId?.let { sessions[it] }?.snapshot?.teaId
    }.distinctUntilChanged()

    override suspend fun archive(teaId: String): ArchiveResult {
        val tea = s.collection.teas.getTea(teaId)
        return when {
            s.activeTeaId() == teaId -> ArchiveResult.InActiveSession

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

    override suspend fun delete(teaId: String): DeleteResult = when {
        s.collection.teas.getTea(teaId) == null -> DeleteResult.NotFound

        s.activeTeaId() == teaId -> DeleteResult.InActiveSession

        else -> {
            val recipeIds = s.collection.recipeRows.value.values.filter { it.teaId == teaId }.map { it.id }.toSet()
            s.sessionRows.value.values.filter { it.snapshot.teaId == teaId }.forEach { session ->
                s.updateSession(session.id) {
                    it.copy(
                        snapshot = it.snapshot.copy(
                            teaId = null,
                            profileId = it.snapshot.profileId?.takeIf { id -> id !in recipeIds },
                        ),
                    )
                }
            }
            s.collection.teas.deleteTea(teaId)
            deleted += teaId
            DeleteResult.Deleted
        }
    }
}

/**
 * [VesselArchive] on [FakeSessions] and its collection, as on Room: the active brew session's vessel
 * can't be archived; [archived] records every archive.
 */
class FakeVesselArchive(private val s: FakeSessions) : VesselArchive {
    val archived = mutableListOf<String>()

    override fun observeActiveVesselId(): Flow<String?> = combine(s.activeRows, s.sessionRows) { active, sessions ->
        active[TimerSlot.BREW]?.activeBrew?.sessionId?.let { sessions[it] }?.snapshot?.vesselId
    }.distinctUntilChanged()

    override suspend fun archive(vesselId: String): ArchiveResult {
        val vessel = s.collection.vessels.getVessel(vesselId)
        val active = s.activeRows.value[TimerSlot.BREW]?.activeBrew?.sessionId
            ?.let { s.sessionRows.value[it] }?.snapshot?.vesselId
        return when {
            active == vesselId -> ArchiveResult.InActiveSession

            vessel == null -> ArchiveResult.NotFound

            else -> {
                s.collection.vessels.saveVessel(vessel.copy(isArchived = true))
                archived += vesselId
                ArchiveResult.Archived
            }
        }
    }

    override suspend fun restore(vesselId: String) {
        s.collection.vessels.getVessel(vesselId)?.let { s.collection.vessels.saveVessel(it.copy(isArchived = false)) }
    }
}

/** The tea of the active brew session (E15), as `active_brew` joined with its session. */
private fun FakeSessions.activeTeaId(): String? =
    activeRows.value[TimerSlot.BREW]?.activeBrew?.sessionId?.let { sessionRows.value[it] }?.snapshot?.teaId
