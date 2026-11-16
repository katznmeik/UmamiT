package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.TimerSnapshot
import javax.inject.Inject

/**
 * [ActiveBrewStore] on Room (05 §3.12): a slot's row and, for a brew session, its step rows in one
 * transaction.
 */
class RoomActiveBrewStore @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    ActiveBrewStore {
    private val activeBrew = db.activeBrewDao()
    private val sessions = db.sessionDao()

    override suspend fun read(slot: TimerSlot): TimerSnapshot? = db.withTransaction {
        activeBrew.get(slot)?.let { snapshotOf(it) }
    }

    override suspend fun persist(slot: TimerSlot, snapshot: TimerSnapshot?) {
        if (snapshot == null) return clear(slot)
        require(snapshot.activeBrew.fitsIn(slot)) { "a ${snapshot.activeBrew.slot} snapshot is not stored as $slot" }
        val now = time.nowMs()
        db.withTransaction { write(slot, snapshot, now) }
    }

    override suspend fun clear(slot: TimerSlot) = activeBrew.delete(slot)

    /** The snapshot of [row] with its session's step rows; called inside a transaction. */
    private suspend fun snapshotOf(row: ActiveBrewEntity): TimerSnapshot {
        val steps = row.sessionId?.let { sessions.steps(it).map { step -> step.toRow() } }.orEmpty()
        // A cold or ice brew has long steps only; that follows from the session's method (D-053).
        val method = row.sessionId?.let { sessions.get(it)?.method }
        return TimerSnapshot(
            row.toSnapshot(),
            steps,
            longStepsOnly = method?.let(BrewTimerEngine::longStepsOnly) ?: false,
        )
    }

    /** Writes [snapshot]'s row as [slot]'s and its session's step rows; called inside a transaction. */
    private suspend fun write(slot: TimerSlot, snapshot: TimerSnapshot, now: Long) {
        val row = snapshot.activeBrew
        row.sessionId?.let { syncSteps(it, snapshot, now) }
        val createdAt = activeBrew.get(slot)?.createdAt ?: now
        activeBrew.upsert(row.toEntity(slot = slot, createdAt = createdAt, updatedAt = now))
    }

    /**
     * Makes the session's rows equal [snapshot]'s step rows, keeping row ids, notes and creation times. The infusions'
     * taste and notes are never written here (05 §3.7, D-114): the update leaves their columns out.
     */
    private suspend fun syncSteps(sessionId: String, snapshot: TimerSnapshot, now: Long) {
        val existing = sessions.steps(sessionId).associateBy { it.position }
        val changed = snapshot.steps.mapNotNull { row ->
            existing[row.position]?.let { old -> old.with(row, now).takeIf { it !== old } }
        }
        sessions.deleteStepsFrom(sessionId, snapshot.steps.size)
        sessions.updateSteps(changed.map { it.timerColumns() })
        sessions.insertSteps(snapshot.steps.filter { it.position !in existing }.map { it.toEntity(sessionId, now) })
    }
}
