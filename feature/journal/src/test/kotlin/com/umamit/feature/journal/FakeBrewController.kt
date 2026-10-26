package com.umamit.feature.journal

import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.timer.controller.SavedSheetPort
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.runBlocking

/**
 * Stands in for the BrewSessionController at its two seams the journal sees: the
 * session repository's data effects and the saved sheet port, in the order the
 * controller runs them for *Sitzung beenden* (CreateSession, RecordStep …,
 * CompleteSession, ShowSavedSheet; 04 §4, D-047).
 */
class FakeBrewController(
    private val sessions: SessionRepository,
    private val savedSheet: SavedSheetPort,
    private val startedAtMs: () -> Long = { JournalSessions.ms(java.time.LocalDateTime.of(2026, 10, 22, 9, 12)) },
) {
    fun start(id: String, vararg plannedSeconds: Int, teaId: String? = null) = runBlocking {
        sessions.startSession(
            SessionStart(
                id = id,
                snapshot = SNAPSHOT.copy(teaId = teaId),
                steps = plannedSeconds.map { PlannedStep(StepKind.INFUSION, it, tempC = 55) },
                preAlertSeconds = 10,
                startedAtMs = startedAtMs(),
                zoneId = JournalSessions.BERLIN.id,
            ),
        )
    }

    /** Pours [stepIndex] after exactly its planned time. */
    fun pour(id: String, stepIndex: Int, seconds: Int) = runBlocking {
        val start = startedAtMs() + stepIndex * STEP_GAP_MS
        sessions.recordStep(
            id,
            stepIndex,
            StepRecord(
                plannedSeconds = seconds,
                actualSeconds = seconds,
                pausedSeconds = 0,
                overtimeSeconds = 0,
                startedAtWallMs = start,
                endedAtWallMs = start + seconds * 1_000L,
                skipped = false,
                overtimeAutoAck = false,
            ),
        )
    }

    /** *Sitzung beenden*: the session is completed, then the sheet is due. */
    fun finish(id: String) = runBlocking {
        sessions.completeSession(id, autoFinished = false)
        savedSheet.showSavedSheet(id)
    }

    /** A whole session of the tea [teaId] (none: without a tea) whose every step is poured on time, then finished. */
    fun brew(id: String, vararg seconds: Int, teaId: String? = null) {
        start(id, *seconds, teaId = teaId)
        seconds.forEachIndexed { index, s -> pour(id, index, s) }
        finish(id)
    }

    private companion object {
        const val STEP_GAP_MS = 120_000L

        val SNAPSHOT = SessionSnapshot(
            teaId = null,
            teaName = "Bio Sencha Hisui",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = null,
            vesselName = "Kyusu",
            leafGrams = 4.0,
            waterMl = 250,
            baseTempC = 55,
        )
    }
}
