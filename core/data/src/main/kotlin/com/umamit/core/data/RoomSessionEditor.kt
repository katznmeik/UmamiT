package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.common.snap5
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import java.math.RoundingMode
import javax.inject.Inject

/**
 * [SessionEditor] on Room (05 §3.7 „Edit", JRN-11, D-112): one transaction on a `COMPLETED` session. It reads and
 * writes the session, its step rows and its BREW stock event only; `active_brew` and the tea are never written.
 */
class RoomSessionEditor @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    SessionEditor {
    private val sessions = db.sessionDao()
    private val inventory = db.inventoryDao()

    override suspend fun editable(sessionId: String): EditableSession? = db.withTransaction {
        val row =
            sessions.get(sessionId)?.takeIf { it.status == SessionStatus.COMPLETED } ?: return@withTransaction null
        EditableSession(
            session = row.toModel(sessions.steps(sessionId), db.tastingDao().descriptors(sessionId)),
            hasStockDeduction = brewEvent(row) != null,
        )
    }

    override suspend fun editSession(sessionId: String, edit: SessionEdit): SessionEditResult {
        if (!edit.inBounds()) return SessionEditResult.INVALID
        val now = time.nowMs()
        return db.withTransaction {
            val stored = sessions.get(sessionId)?.takeIf { it.status == SessionStatus.COMPLETED }
                ?: return@withTransaction SessionEditResult.NOT_COMPLETED
            val rows = sessions.steps(sessionId)
            val shift = edit.startedAtMs?.let { it - stored.startedAt } ?: 0L
            val brewed = rows.filter { it.actualSeconds != null && !it.skipped }.map { it.position }.toSet()
            when {
                // Only a moved start is refused: an unmoved one stays valid even after the clock was set back.
                shift != 0L && (stored.endedAt ?: stored.startedAt) + shift > now -> SessionEditResult.IN_FUTURE

                edit.steps.any { it.position !in brewed } -> SessionEditResult.INVALID

                else -> {
                    val vessel = edit.vessel?.takeIf { it.changes(stored) }
                    val vesselName = vessel?.vesselId?.let { id -> db.vesselDao().get(id)?.name }
                    if (vessel?.vesselId != null && vesselName == null) {
                        SessionEditResult.INVALID
                    } else {
                        apply(stored, rows, edit, shift, vessel, vesselName, now)
                        SessionEditResult.SAVED
                    }
                }
            }
        }
    }

    /** Writes the edit; called inside the transaction, after every check. */
    @Suppress("LongParameterList") // the checked parts of one edit
    private suspend fun apply(
        stored: BrewSessionEntity,
        rows: List<SessionStepEntity>,
        edit: SessionEdit,
        shift: Long,
        vessel: VesselChange?,
        vesselName: String?,
        now: Long,
    ) {
        val brew = brewEvent(stored)
        val leaf = edit.leafGrams?.takeIf { it != stored.leafGrams }
        val endedAt = stored.endedAt?.plus(shift)
        sessions.update(
            stored.copy(
                startedAt = stored.startedAt + shift,
                endedAt = endedAt,
                vesselId = if (vessel != null) vessel.vesselId else stored.vesselId,
                vesselName = if (vessel != null) vesselName else stored.vesselName,
                leafGrams = leaf ?: stored.leafGrams,
                costCents = if (leaf != null) newCost(stored, leaf) else stored.costCents,
                waterMl = edit.waterMl ?: stored.waterMl,
                updatedAt = now,
            ),
        )
        val stepEdits = edit.steps.associateBy { it.position }
        val changed = rows.mapNotNull { row -> row.edited(shift, stepEdits[row.position], now) }
        if (changed.isNotEmpty()) sessions.updateSteps(changed.map { it.timerColumns() })
        if (brew != null) {
            when {
                leaf != null && leaf <= 0.0 -> inventory.delete(brew.id)

                leaf != null || shift != 0L -> inventory.upsert(
                    brew.copy(
                        deltaGrams = -(leaf ?: -brew.deltaGrams),
                        occurredAt = endedAt ?: brew.occurredAt,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    /** The session's BREW stock event (at most one per session, INV-02); none for a session whose tea is gone. */
    private suspend fun brewEvent(session: BrewSessionEntity): InventoryEventEntity? = session.teaId?.let { teaId ->
        inventory.eventsForTea(teaId).firstOrNull { it.sessionId == session.id && it.type == InventoryEventType.BREW }
    }

    /**
     * The cost after a leaf change: the finish rule (leaf × the tea's cost per gram now, INV-08) for a session with
     * its tea; for one whose tea is gone the old cost × new leaf ÷ old leaf, half up (null stays null).
     */
    private suspend fun newCost(stored: BrewSessionEntity, leaf: Double): Long? {
        val teaId = stored.teaId
        val oldCost = stored.costCents
        val oldLeaf = stored.leafGrams?.takeIf { it > 0.0 }
        return when {
            teaId != null -> costCents(leaf.takeIf { it > 0.0 }, inventory.eventsForTea(teaId).map { it.toModel() })

            oldCost == null || oldLeaf == null -> oldCost

            else -> (oldCost.toBigDecimal() * leaf.toBigDecimal())
                .divide(oldLeaf.toBigDecimal(), 0, RoundingMode.HALF_UP)
                .toLong()
        }
    }
}

/**
 * Whether this vessel differs from [stored]'s; VesselChange(null) also clears the name kept for a deleted vessel
 * (05 §3.7: both null).
 */
private fun VesselChange.changes(stored: BrewSessionEntity): Boolean =
    vesselId != stored.vesselId || (vesselId == null && stored.vesselName != null)

/** Whether every given value is within [SessionEditLimits]. */
private fun SessionEdit.inBounds(): Boolean {
    val leafOk = leafGrams?.let { it >= 0.0 && it <= SessionEditLimits.MAX_LEAF_GRAMS && !it.isNaN() } ?: true
    val waterOk = waterMl?.let { it in SessionEditLimits.WATER_ML } ?: true
    val stepsOk = steps.all {
        it.actualSeconds in SessionEditLimits.STEP_SECONDS && (it.tempC == null || snap5(it.tempC) in TEMP_RANGE)
    }
    return leafOk && waterOk && stepsOk && steps.map { it.position }.distinct().size == steps.size
}

private val TEMP_RANGE = SessionEditLimits.TEMP_C

private const val MS_PER_SECOND = 1_000L

/**
 * This row moved by [shift] ms and with [edit]'s recorded time and temperature: `overtime_seconds` = max(0, actual −
 * planned), `ended_at` = `started_at` + actual + paused. Null when nothing changes.
 */
private fun SessionStepEntity.edited(shift: Long, edit: StepEdit?, now: Long): SessionStepEntity? {
    val moved = copy(startedAt = startedAt?.plus(shift), endedAt = endedAt?.plus(shift))
    val result = if (edit == null) {
        moved
    } else {
        val actual = edit.actualSeconds
        moved.copy(
            actualSeconds = actual,
            overtimeSeconds = maxOf(0, actual - plannedSeconds),
            tempC = edit.tempC?.let(::snap5) ?: tempC,
            endedAt = moved.startedAt?.let { it + (actual + (pausedSeconds ?: 0)) * MS_PER_SECOND } ?: moved.endedAt,
        )
    }
    return result.takeIf { it != this }?.copy(updatedAt = now)
}
