package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The recent sessions Start shows (SCR-02, BRW-11): completed sessions,
 * newest first, at most `limit`, each with its steps; the session in progress
 * is not among them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentSessionsTest {
    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    /** Completes session [id] of [seconds] steps after its first [poured] steps (all by default). */
    private suspend fun completed(id: String, startedAtMs: Long, vararg seconds: Int, poured: Int = seconds.size) {
        if (data.teas.getTea("t1") == null) data.teas.saveTea(tea("t1"))
        data.sessions.startSession(
            SessionStart(
                id = id,
                snapshot = snapshotOf(),
                steps = seconds.map { infusion(it) },
                preAlertSeconds = 10,
                startedAtMs = startedAtMs,
                zoneId = "Europe/Berlin",
            ),
        )
        (0 until poured).forEach { i ->
            data.sessions.recordStep(
                id,
                i,
                StepRecord(seconds[i], seconds[i], 0, 0, startedAtMs, startedAtMs + 1_000, false, false),
            )
        }
        data.sessions.completeSession(id, autoFinished = false)
    }

    @Test
    fun `completed sessions come newest first with their steps, limited, without the one in progress`() = runTest {
        completed("old", startedAtMs = 1_000, 45)
        completed("mid", startedAtMs = 2_000, 60, 10)
        completed("new", startedAtMs = 3_000, 30)
        data.sessions.startSession(
            SessionStart("running", snapshotOf(), listOf(infusion(60)), 10, 4_000, "Europe/Berlin"),
        )

        val recent = data.sessions.observeRecentSessions(limit = 2).first()

        assertThat(recent.map { it.id }).containsExactly("new", "mid").inOrder()
        assertThat(recent[1].steps.map { it.kind to it.plannedSeconds })
            .containsExactly(StepKind.INFUSION to 60, StepKind.INFUSION to 10).inOrder()
    }

    @Test
    fun `a newly completed session shows up`() = runTest {
        completed("a", startedAtMs = 1_000, 45)
        assertThat(data.sessions.observeRecentSessions(limit = 3).first().map { it.id }).containsExactly("a")

        completed("b", startedAtMs = 2_000, 45)

        assertThat(data.sessions.observeRecentSessions(limit = 3).first().map { it.id })
            .containsExactly("b", "a").inOrder()
    }

    @Test
    fun `a session finished early comes with its never-started rows, and its ended steps are the poured ones (Q-19)`() =
        runTest {
            completed("early", startedAtMs = 1_000, 60, 10, 30, poured = 2)

            val early = data.sessions.observeRecentSessions(limit = 1).first().single()

            // From v0.4.0 the third row is kept for *So nochmal brühen*; what Start counts and brews again
            // are the ended rows, as v0.3.0 stored them (BRW-11, D-062).
            assertThat(early.plannedStepCount).isEqualTo(3)
            assertThat(early.steps.map { it.plannedSeconds to it.actualSeconds })
                .containsExactly(60 to 60, 10 to 10, 30 to null).inOrder()
            assertThat(early.endedSteps.map { it.plannedSeconds }).containsExactly(60, 10).inOrder()
        }
}
