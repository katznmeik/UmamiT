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

    private suspend fun completed(id: String, startedAtMs: Long, vararg seconds: Int) {
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
        seconds.indices.forEach { i ->
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
}
