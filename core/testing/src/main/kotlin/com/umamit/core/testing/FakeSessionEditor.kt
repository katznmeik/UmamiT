package com.umamit.core.testing

import com.umamit.core.common.snap5
import com.umamit.core.data.EditableSession
import com.umamit.core.data.SessionEdit
import com.umamit.core.data.SessionEditLimits
import com.umamit.core.data.SessionEditResult
import com.umamit.core.data.SessionEditor
import com.umamit.core.data.StepEdit
import com.umamit.core.model.BrewSession
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StockSummary
import kotlinx.coroutines.flow.update
import java.io.IOException
import kotlin.math.roundToLong

/**
 * Fake [SessionEditor] on [FakeSessions]' rows with Room's rules (05 §3.7 „Edit"): a `COMPLETED` session only, the
 * bounds, the shift refused into the future (against [FakeSessions]' clock), the BREW event moved, set or deleted at
 * 0 and never created, the cost by the finish rule or scaled for a tea that is gone. [failNext] makes the next write
 * throw (a failed write, SCR-17); [writes] counts the calls.
 */
class FakeSessionEditor(private val s: FakeSessions) : SessionEditor {
    var writes = 0
        private set

    /** The next [editSession] throws an [IOException] and writes nothing. */
    var failNext = false

    override suspend fun editable(sessionId: String): EditableSession? =
        s.sessionRows.value[sessionId]?.takeIf { it.status == SessionStatus.COMPLETED }?.let {
            EditableSession(it, hasStockDeduction = brewEvent(sessionId) != null)
        }

    override suspend fun editSession(sessionId: String, edit: SessionEdit): SessionEditResult {
        writes++
        if (failNext) {
            failNext = false
            throw IOException("fake write failure")
        }
        val stored = s.sessionRows.value[sessionId]?.takeIf { it.status == SessionStatus.COMPLETED }
        val shift = stored?.let { edit.startedAtMs?.minus(it.startedAtMs) } ?: 0L
        val brewed = stored?.steps.orEmpty().filter { it.actualSeconds != null && !it.skipped }.map { it.position }
        val vessel = edit.vessel?.takeIf { it.vesselId != stored?.snapshot?.vesselId }
        val vesselName = vessel?.vesselId?.let { s.collection.vesselRows.value[it]?.name }
        return when {
            !edit.inBounds() -> SessionEditResult.INVALID

            stored == null -> SessionEditResult.NOT_COMPLETED

            (stored.endedAtMs ?: stored.startedAtMs) + shift > s.nowMs() -> SessionEditResult.IN_FUTURE

            edit.steps.any { it.position !in brewed } -> SessionEditResult.INVALID

            vessel?.vesselId != null && vesselName == null -> SessionEditResult.INVALID

            else -> {
                apply(stored, edit, shift, vessel != null, vesselName)
                SessionEditResult.SAVED
            }
        }
    }

    private fun apply(stored: BrewSession, edit: SessionEdit, shift: Long, newVessel: Boolean, vesselName: String?) {
        val leaf = edit.leafGrams?.takeIf { it != stored.snapshot.leafGrams }
        val endedAt = stored.endedAtMs?.plus(shift)
        val stepEdits = edit.steps.associateBy { it.position }
        s.updateSession(stored.id) { session ->
            session.copy(
                startedAtMs = session.startedAtMs + shift,
                endedAtMs = endedAt,
                snapshot = session.snapshot.copy(
                    vesselId = if (newVessel) edit.vessel?.vesselId else session.snapshot.vesselId,
                    vesselName = if (newVessel) vesselName else session.snapshot.vesselName,
                    leafGrams = leaf ?: session.snapshot.leafGrams,
                    waterMl = edit.waterMl ?: session.snapshot.waterMl,
                ),
                costCents = if (leaf != null) cost(session, leaf) else session.costCents,
                steps = session.steps.map { row -> row.edited(shift, stepEdits[row.position]) },
            )
        }
        val brew = brewEvent(stored.id) ?: return
        s.collection.eventRows.update { rows ->
            when {
                leaf != null && leaf <= 0.0 -> rows - brew.id

                else -> rows + (
                    brew.id to brew.copy(
                        deltaGrams = -(leaf ?: -brew.deltaGrams),
                        occurredAtMillis = endedAt ?: brew.occurredAtMillis,
                    )
                    )
            }
        }
    }

    private fun cost(session: BrewSession, leaf: Double): Long? {
        val teaId = session.snapshot.teaId?.takeIf { it in s.collection.teaRows.value }
        val oldCost = session.costCents
        val oldLeaf = session.snapshot.leafGrams?.takeIf { it > 0.0 }
        return when {
            teaId != null -> StockSummary.of(s.collection.eventRows.value.values.filter { it.teaId == teaId })
                .costPerGramCents
                ?.takeIf { leaf > 0.0 }
                ?.let { (leaf * it).roundToLong() }

            oldCost == null || oldLeaf == null -> oldCost

            else -> (oldCost * leaf / oldLeaf).roundToLong()
        }
    }

    /** The row moved by [shift] ms and with [step]'s recorded time and temperature, as on Room. */
    private fun SessionStepRow.edited(shift: Long, step: StepEdit?): SessionStepRow {
        val moved = copy(startedAtWallMs = startedAtWallMs?.plus(shift), endedAtWallMs = endedAtWallMs?.plus(shift))
        if (step == null) return moved
        return moved.copy(
            actualSeconds = step.actualSeconds,
            overtimeSeconds = maxOf(0, step.actualSeconds - plannedSeconds),
            tempC = step.tempC?.let(::snap5) ?: tempC,
            endedAtWallMs = moved.startedAtWallMs
                ?.let { it + (step.actualSeconds + (pausedSeconds ?: 0)) * MS_PER_SECOND }
                ?: moved.endedAtWallMs,
        )
    }

    private fun brewEvent(sessionId: String): InventoryEvent? = s.collection.eventRows.value.values.firstOrNull {
        it.sessionId == sessionId &&
            it.type == InventoryEventType.BREW
    }

    private fun SessionEdit.inBounds(): Boolean {
        val leafOk = leafGrams?.let { it >= 0.0 && it <= SessionEditLimits.MAX_LEAF_GRAMS } ?: true
        val waterOk = waterMl?.let { it in SessionEditLimits.WATER_ML } ?: true
        val stepsOk = steps.all {
            it.actualSeconds in SessionEditLimits.STEP_SECONDS && (it.tempC?.let { t -> snap5(t) in SessionEditLimits.TEMP_C } ?: true)
        }
        return leafOk && waterOk && stepsOk && steps.map { it.position }.distinct().size == steps.size
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
