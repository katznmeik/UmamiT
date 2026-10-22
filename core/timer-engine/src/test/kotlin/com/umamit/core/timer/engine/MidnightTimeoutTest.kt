package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.timer.engine.TimerEffect.CompleteSession
import com.umamit.core.timer.engine.TimerEffect.DiscardSession
import com.umamit.core.timer.engine.TimerEffect.NotifySessionSaved
import com.umamit.core.timer.engine.TimerEffect.Persist
import com.umamit.core.timer.engine.TimerEffect.RecordStep
import com.umamit.core.timer.engine.TimerEffect.ShowSavedSheet
import com.umamit.core.timer.engine.TimerEffect.StopForeground
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The midnight timeout (TMR-14, 04 §3.2): a waiting session ends at the first
 * local midnight at least 6 h after its last interaction, in the session's
 * time zone and across daylight-saving changes. Expected instants are UTC
 * literals, so they do not depend on the engine's own date arithmetic.
 */
class MidnightTimeoutTest {

    private fun ms(utc: String): Long = Instant.parse(utc).toEpochMilli()

    /** A brew session in Ready whose last interaction (its start) was at [utc] in [zone]. */
    private fun readyAt(utc: String, zone: ZoneId = BERLIN) = EngineDriver(
        VirtualClocks(wall = ms(utc), zoneId = zone),
    ).apply { send(startSession(infusion(60), infusion(20))) }

    @Test
    fun `due at the first local midnight at least 6 h after the last interaction`() {
        // (last interaction, midnight timeout), both in UTC; Berlin is UTC+2 (CEST) until 25 Oct 2026, then UTC+1.
        val cases = listOf(
            // 10:00 CEST → 6 h later 16:00 → the coming midnight.
            "2026-10-22T08:00:00Z" to "2026-10-22T22:00:00Z",
            // 17:59:59 → 23:59:59, still the coming midnight.
            "2026-10-22T15:59:59Z" to "2026-10-22T22:00:00Z",
            // 18:00 → exactly midnight, which counts.
            "2026-10-22T16:00:00Z" to "2026-10-22T22:00:00Z",
            // 18:00:01 → just past midnight → the next-but-one midnight.
            "2026-10-22T16:00:01Z" to "2026-10-23T22:00:00Z",
            // 21:00 → 03:00 → the next-but-one midnight (TMR-14 example).
            "2026-10-22T19:00:00Z" to "2026-10-23T22:00:00Z",
            // 00:30 → 06:30 → the following midnight.
            "2026-10-22T22:30:00Z" to "2026-10-23T22:00:00Z",
        )
        cases.forEach { (interaction, midnight) ->
            assertWithMessage(interaction).that(BrewTimerEngine.dueTimes(readyAt(interaction).state).midnightAtWallMs)
                .isEqualTo(ms(midnight))
        }
    }

    @Test
    fun `due times are right across both daylight-saving changes in Berlin`() {
        val cases = listOf(
            // Spring forward on Sun 29 Mar 2026, 02:00 CET → 03:00 CEST.
            // Sat 17:00 CET → 23:00 → midnight Sat/Sun is still CET (UTC+1).
            "2026-03-28T16:00:00Z" to "2026-03-28T23:00:00Z",
            // Sat 21:00 CET → 6 h later is Sun 04:00 CEST → midnight Sun/Mon, already CEST (UTC+2).
            "2026-03-28T20:00:00Z" to "2026-03-29T22:00:00Z",
            // Sun 10:00 CEST → 16:00 → midnight Sun/Mon in CEST: the day had only 23 h.
            "2026-03-29T08:00:00Z" to "2026-03-29T22:00:00Z",
            // Fall back on Sun 25 Oct 2026, 03:00 CEST → 02:00 CET.
            // Sat 17:00 CEST → 23:00 → midnight Sat/Sun is still CEST (UTC+2).
            "2026-10-24T15:00:00Z" to "2026-10-24T22:00:00Z",
            // Sat 21:00 CEST → 6 h later is Sun 02:00 CET → midnight Sun/Mon, now CET (UTC+1).
            "2026-10-24T19:00:00Z" to "2026-10-25T23:00:00Z",
            // Sun 10:00 CET → 16:00 → midnight Sun/Mon in CET: the day had 25 h.
            "2026-10-25T09:00:00Z" to "2026-10-25T23:00:00Z",
            // Sun 17:30 CET: 6 h are real hours, 23:30 → the coming midnight.
            "2026-10-25T16:30:00Z" to "2026-10-25T23:00:00Z",
        )
        cases.forEach { (interaction, midnight) ->
            assertWithMessage(interaction).that(BrewTimerEngine.dueTimes(readyAt(interaction).state).midnightAtWallMs)
                .isEqualTo(ms(midnight))
        }
    }

    @Test
    fun `a day without a midnight ends the session at the start of that day`() {
        // São Paulo skipped 00:00 → 01:00 on 4 Nov 2018; the day started at 01:00 −02:00.
        val driver = readyAt("2018-11-03T15:00:00Z", ZoneId.of("America/Sao_Paulo"))

        assertThat(BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs).isEqualTo(ms("2018-11-04T03:00:00Z"))
    }

    @Test
    fun `the session's time zone counts, not the device's current zone`() {
        val driver = readyAt("2026-10-22T08:00:00Z")
        driver.clocks.zoneId = ZoneId.of("Asia/Tokyo")
        driver.send(TimerEvent.AddStep)

        assertThat(BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs).isEqualTo(ms("2026-10-22T22:00:00Z"))
    }

    @Test
    fun `an interaction moves the midnight timeout`() {
        val driver = readyAt("2026-10-22T19:00:00Z")
        driver.clocks.wall = ms("2026-10-23T07:00:00Z")
        driver.send(TimerEvent.AddStep)
        assertThat(BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs).isEqualTo(ms("2026-10-23T22:00:00Z"))

        driver.clocks.wall = ms("2026-10-23T17:00:00Z")
        driver.send(TimerEvent.AddStep)
        assertThat(BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs).isEqualTo(ms("2026-10-24T22:00:00Z"))
    }

    @Test
    fun `only a waiting brew session has a midnight timeout, also when demoted`() {
        val running = readyAt("2026-10-22T08:00:00Z").apply { send(TimerEvent.Start) }
        val quick = EngineDriver().apply { send(TimerEvent.StartQuickTimer(60, globalPreAlertSeconds = 10)) }
        assertThat(BrewTimerEngine.dueTimes(running.state).midnightAtWallMs).isNull()
        assertThat(BrewTimerEngine.dueTimes(quick.state).midnightAtWallMs).isNull()
        assertThat(BrewTimerEngine.dueTimes(TimerState.Idle).midnightAtWallMs).isNull()

        val demoted = readyAt("2026-10-22T08:00:00Z").apply {
            clocks.advance(30 * 60_000L)
            send(TimerEvent.IdleDemote)
        }
        assertThat(BrewTimerEngine.dueTimes(demoted.state).midnightAtWallMs).isEqualTo(ms("2026-10-22T22:00:00Z"))
    }

    @Test
    fun `after a long step ended by the overtime timeout the 6 h count from then`() {
        // A 7 h cold brew started at 18:00 CEST ends at 01:00, times out at 01:10, and waits in Finished.
        val driver = EngineDriver(VirtualClocks(wall = ms("2026-10-22T16:00:00Z"))).apply {
            send(startSession(infusion(7 * 3600)))
            send(TimerEvent.Start)
            clocks.advanceSeconds(7 * 3600L)
            send(TimerEvent.DeadlineReached)
            clocks.advance(10 * 60_000L)
            send(TimerEvent.OvertimeTimeout)
        }

        assertThat(driver.state).isInstanceOf(TimerState.Finished::class.java)
        assertThat(BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs).isEqualTo(ms("2026-10-23T22:00:00Z"))
    }

    @Test
    fun `a session with a completed non-rinse step is completed with the notification, silently`() {
        val driver = readyAt("2026-10-22T08:00:00Z").apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(60)
            send(TimerEvent.DeadlineReached)
            send(TimerEvent.Poured)
        }
        val midnight = BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs!!
        driver.clocks.wall = midnight - 1
        assertThat(driver.send(TimerEvent.MidnightTimeout)).isEmpty()
        assertThat(driver.outcome).isEqualTo(TransitionOutcome.Ignored)
        driver.clocks.wall = midnight

        val effects = driver.send(TimerEvent.MidnightTimeout)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(
            CompleteSession(SESSION_ID, autoFinished = true),
            Persist,
            NO_ALARMS,
            StopForeground,
            NotifySessionSaved(SESSION_ID),
        ).inOrder()
    }

    @Test
    fun `a session without a completed non-rinse step is discarded silently`() {
        val driver = EngineDriver(VirtualClocks(wall = ms("2026-10-22T08:00:00Z"))).apply {
            send(startSession(rinse(10), infusion(60)))
            send(TimerEvent.Start)
            clocks.advanceSeconds(10)
            send(TimerEvent.DeadlineReached)
            send(TimerEvent.Poured)
        }
        driver.clocks.wall = BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs!!

        val effects = driver.send(TimerEvent.MidnightTimeout)

        assertThat(driver.state).isEqualTo(TimerState.Idle)
        assertThat(effects).containsExactly(DiscardSession(SESSION_ID), Persist, NO_ALARMS, StopForeground).inOrder()
    }

    @Test
    fun `a paused step ends where the pause began, without the open pause`() {
        val driver = readyAt("2026-10-22T08:00:00Z").apply {
            send(TimerEvent.Start)
            clocks.advanceSeconds(20)
            send(TimerEvent.Pause)
            clocks.advanceSeconds(5)
            send(TimerEvent.Resume)
            clocks.advanceSeconds(10)
            send(TimerEvent.Pause)
        }
        val pausedAt = driver.clocks.wall
        driver.clocks.wall = BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs!!
        driver.clocks.elapsed += 14 * 3600_000L

        val effects = driver.send(TimerEvent.MidnightTimeout)

        val record = StepRecord(
            plannedSeconds = 60,
            actualSeconds = 30,
            pausedSeconds = 5,
            overtimeSeconds = 0,
            startedAtWallMs = ms("2026-10-22T08:00:00Z"),
            endedAtWallMs = pausedAt,
            skipped = false,
            overtimeAutoAck = false,
        )
        assertThat(effects).containsExactly(
            RecordStep(SESSION_ID, 0, record),
            CompleteSession(SESSION_ID, autoFinished = true),
            Persist,
            NO_ALARMS,
            StopForeground,
            NotifySessionSaved(SESSION_ID),
        ).inOrder()
        assertThat(effects.none { it is ShowSavedSheet }).isTrue()
    }

    @Test
    fun `a paused rinse alone is discarded`() {
        val driver = EngineDriver(VirtualClocks(wall = ms("2026-10-22T08:00:00Z"))).apply {
            send(startSession(rinse(20), infusion(60)))
            send(TimerEvent.Start)
            clocks.advanceSeconds(5)
            send(TimerEvent.Pause)
        }
        driver.clocks.wall = BrewTimerEngine.dueTimes(driver.state).midnightAtWallMs!!

        assertThat(driver.send(TimerEvent.MidnightTimeout))
            .containsExactly(DiscardSession(SESSION_ID), Persist, NO_ALARMS, StopForeground).inOrder()
    }
}
