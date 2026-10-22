package com.umamit.core.timer.alert

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The ringer-mode matrix, pre-alert, repeat limit and stop of 04 §9.1 (M1-03a, #16). */
class RingerModeAlertPlayerTest {
    private val scheduler = FakeScheduler()
    private val environment = FakeEnvironment()
    private val sound = FakeSoundOutput(scheduler)
    private val haptics = FakeHaptics(scheduler)
    private var settings = AlertSettings()
    private val player = RingerModeAlertPlayer(environment, sound, haptics, scheduler) { settings }

    // --- Alert: ringer-mode matrix -------------------------------------------

    @Test
    fun normalModeAlertPlaysToneWithFocusAndVibrationPattern() {
        ringer(RingerMode.NORMAL)

        player.startAlert()

        assertThat(sound.events).containsExactly("requestFocus @ 0", "play KLANGSCHALE @ 0").inOrder()
        assertThat(sound.focusHeld).isTrue()
        assertThat(haptics.events).containsExactly("play ALERT @ 0")
    }

    @Test
    fun vibrateModeAlertVibratesOnly() {
        ringer(RingerMode.VIBRATE)

        player.startAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).containsExactly("play ALERT @ 0")
    }

    @Test
    fun silentModeAlertIsVisualOnly() {
        ringer(RingerMode.SILENT)

        player.startAlert()
        scheduler.advanceBy(60_000L)

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun inACallTheAlertVibratesOnly() {
        environment.conditions = AlertConditions(RingerMode.NORMAL, inCall = true)

        player.startAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).containsExactly("play ALERT @ 0")
    }

    @Test
    fun doNotDisturbBlockingAlertsKeepsTheAlertVisual() {
        environment.conditions = AlertConditions(RingerMode.NORMAL, dndBlocksAlerts = true)

        player.startAlert()
        player.playPreAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun vibrationSettingOffKeepsOnlyTheToneInNormalMode() {
        settings = AlertSettings(vibration = false)
        ringer(RingerMode.NORMAL)

        player.startAlert()

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun matrixTable() {
        fun outputs(mode: RingerMode, inCall: Boolean = false, dnd: Boolean = false) =
            AlertMatrix.outputs(AlertConditions(mode, inCall, dnd))

        assertThat(outputs(RingerMode.NORMAL)).isEqualTo(AlertOutputs(sound = true, vibration = true))
        assertThat(outputs(RingerMode.VIBRATE)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.SILENT)).isEqualTo(AlertOutputs.NONE)
        assertThat(outputs(RingerMode.NORMAL, inCall = true)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.VIBRATE, inCall = true)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.SILENT, inCall = true)).isEqualTo(AlertOutputs.NONE)
        RingerMode.entries.forEach { assertThat(outputs(it, dnd = true)).isEqualTo(AlertOutputs.NONE) }
    }

    @Test
    fun alertPatternIsTheSpecPattern() {
        assertThat(Haptic.ALERT.timings).containsExactly(0L, 400L, 200L, 400L, 200L, 800L).inOrder()
    }

    // --- Alert length --------------------------------------------------------

    @Test
    fun normalLengthRepeatsToneAndVibrationForAtMostFifteenSeconds() {
        ringer(RingerMode.NORMAL)

        player.startAlert()
        scheduler.advanceBy(60_000L)

        val starts = listOf(0L, 3_500L, 7_000L, 10_500L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactlyElementsIn(starts).inOrder()
        assertThat(haptics.plays(Haptic.ALERT)).containsExactlyElementsIn(starts).inOrder()
        // The last cycle ends at 14 s ≤ 15 s; then everything is released.
        assertThat(sound.events.last()).isEqualTo("abandonFocus @ 14000")
        assertThat(sound.focusHeld).isFalse()
        assertThat(sound.playing).isNull()
        assertThat(haptics.vibrating).isNull()
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun vibrateModeRepeatsThePatternWithTheSameRhythm() {
        ringer(RingerMode.VIBRATE)

        player.startAlert()
        scheduler.advanceBy(60_000L)

        assertThat(haptics.plays(Haptic.ALERT)).containsExactly(0L, 3_500L, 7_000L, 10_500L).inOrder()
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun shortLengthPlaysOnceAndLongLengthStaysWithinTwoMinutes() {
        settings = AlertSettings(length = AlertLength.SHORT)
        player.startAlert()
        scheduler.advanceBy(60_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)

        settings = AlertSettings(length = AlertLength.LONG)
        player.startAlert()
        scheduler.advanceBy(300_000L)
        val longPlays = sound.plays(AlertSound.KLANGSCHALE).drop(1).map { it - 60_000L }
        assertThat(longPlays).hasSize(34)
        assertThat(longPlays.last() + RingerModeAlertPlayer.cycleMs(AlertSound.KLANGSCHALE)).isAtMost(120_000L)
    }

    @Test
    fun conditionsAreReadAgainAtEveryRepeat() {
        ringer(RingerMode.NORMAL)
        player.startAlert()

        ringer(RingerMode.SILENT)
        scheduler.advanceBy(3_500L)

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.ALERT)).containsExactly(0L)
        assertThat(sound.events).containsAtLeast("stop @ 3500", "abandonFocus @ 3500").inOrder()
    }

    // --- Audio focus ----------------------------------------------------------

    @Test
    fun refusedFocusMeansVibrationOnly() {
        sound.grantFocus = false
        ringer(RingerMode.NORMAL)

        player.startAlert()
        scheduler.advanceBy(60_000L)

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).isEmpty()
        assertThat(sound.events.count { it.startsWith("requestFocus") }).isEqualTo(1)
        assertThat(haptics.plays(Haptic.ALERT)).hasSize(4)
    }

    @Test
    fun lostFocusStopsTheToneForTheRestOfTheAlert() {
        ringer(RingerMode.NORMAL)
        player.startAlert()
        scheduler.advanceBy(1_000L)

        sound.loseFocus()
        scheduler.advanceBy(60_000L)

        assertThat(sound.events).containsAtLeast("stop @ 1000", "abandonFocus @ 1000").inOrder()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.ALERT)).hasSize(4)
    }

    // --- Pre-alert ------------------------------------------------------------

    @Test
    fun preAlertInNormalModeTicksWithAShortHapticAndReleasesTheFocus() {
        ringer(RingerMode.NORMAL)

        player.playPreAlert()

        assertThat(sound.events).containsExactly("requestFocus @ 0", "play TICK @ 0").inOrder()
        assertThat(haptics.events).containsExactly("play PRE_ALERT @ 0")
        scheduler.advanceBy(1_000L)
        assertThat(sound.focusHeld).isFalse()
        assertThat(sound.plays(AlertSound.TICK)).hasSize(1)
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun preAlertInVibrateModeOrInACallIsAShortHapticOnly() {
        ringer(RingerMode.VIBRATE)
        player.playPreAlert()
        environment.conditions = AlertConditions(RingerMode.NORMAL, inCall = true)
        player.playPreAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.plays(Haptic.PRE_ALERT)).hasSize(2)
    }

    @Test
    fun preAlertInSilentModeDoesNothing() {
        ringer(RingerMode.SILENT)

        player.playPreAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun alertRightAfterThePreAlertStartsCleanly() {
        ringer(RingerMode.NORMAL)
        player.playPreAlert()

        player.startAlert()

        assertThat(sound.playing).isEqualTo(AlertSound.KLANGSCHALE)
        assertThat(haptics.vibrating).isEqualTo(Haptic.ALERT)
        scheduler.advanceBy(60_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
    }

    // --- Stop -----------------------------------------------------------------

    @Test
    fun stopAlertStopsSoundAndVibrationImmediatelyAndCancelsTheRepeats() {
        ringer(RingerMode.NORMAL)
        player.startAlert()
        scheduler.advanceBy(5_000L)

        player.stopAlert()

        assertThat(sound.playing).isNull()
        assertThat(sound.focusHeld).isFalse()
        assertThat(haptics.vibrating).isNull()
        assertThat(sound.events.takeLast(2)).containsExactly("stop @ 5000", "abandonFocus @ 5000").inOrder()
        assertThat(haptics.events.last()).isEqualTo("cancel @ 5000")
        assertThat(scheduler.pendingCount).isEqualTo(0)
        scheduler.advanceBy(60_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(2)
    }

    @Test
    fun stopAlertIsIdempotent() {
        ringer(RingerMode.NORMAL)
        player.stopAlert()
        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()

        player.startAlert()
        player.stopAlert()
        val soundEvents = sound.events.toList()
        val hapticEvents = haptics.events.toList()
        player.stopAlert()
        player.stopAlert()

        assertThat(sound.events).isEqualTo(soundEvents)
        assertThat(haptics.events).isEqualTo(hapticEvents)
    }

    @Test
    fun focusLossAfterStopIsIgnored() {
        ringer(RingerMode.NORMAL)
        player.startAlert()
        player.stopAlert()
        val events = sound.events.toList()

        sound.loseFocus()

        assertThat(sound.events).isEqualTo(events)
    }

    private fun ringer(mode: RingerMode) {
        environment.conditions = AlertConditions(mode)
    }
}
