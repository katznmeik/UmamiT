package com.umamit.core.timer.alert

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings
import org.junit.Test

/**
 * The ringer-mode matrix, pre-alert, repeat limit and stop of 04 §9.1 (M1-03a,
 * #16), for every tone and alert length, with the end report and *Alarm
 * testen* (M1-10, #44).
 */
class RingerModeAlertPlayerTest {
    private val scheduler = FakeScheduler()
    private val environment = FakeEnvironment()
    private val sound = FakeSoundOutput(scheduler)
    private val haptics = FakeHaptics(scheduler)

    // „Ting (leise)", the tick of M1: the tests below keep its timing; the other sounds have their own tests (SET-26).
    private var settings = AlertSettings(preAlert = AlertSound.TICK)
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
    fun doNotDisturbTotalSilenceKeepsTheAlertAndTheTickVisual() {
        environment.conditions = AlertConditions(RingerMode.NORMAL, dnd = DoNotDisturb.TOTAL_SILENCE)

        player.startAlert()
        player.playPreAlert()

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun doNotDisturbAlarmsOnlyAndPriorityLetTheAlertRingAsTheRingerMode() {
        // D-098: the cues play on the alarm stream, which „alarms only" and priority mode let through.
        listOf(DoNotDisturb.ALARMS_ONLY, DoNotDisturb.PRIORITY).forEach { dnd ->
            val fresh = FreshPlayer(AlertConditions(RingerMode.NORMAL, dnd = dnd), settings)

            fresh.player.playPreAlert()
            fresh.scheduler.advanceBy(5_000L)
            fresh.player.startAlert()

            assertWithMessage("$dnd").that(fresh.sound.plays(AlertSound.TICK)).containsExactly(0L)
            assertWithMessage("$dnd").that(fresh.sound.plays(AlertSound.KLANGSCHALE)).containsExactly(5_000L)
            assertWithMessage("$dnd").that(fresh.haptics.plays(Haptic.ALERT)).containsExactly(5_000L)
        }
    }

    // --- Bei „Nicht stören" klingeln (SET-32, D-099) -------------------------

    @Test
    fun underDoNotDisturbWithAlarmsAllowedASilentReportRingsTheTickAndEveryCycle() {
        // The PO's v0.3.0-rc.1 test: One UI reports silent under its DND with the phone on ring.
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)

        player.playPreAlert()
        scheduler.advanceBy(5_000L)
        player.startAlert()
        scheduler.advanceBy(60_000L)

        val cycles = listOf(5_000L, 8_500L, 12_000L, 15_500L)
        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.PRE_ALERT)).containsExactly(0L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactlyElementsIn(cycles).inOrder()
        assertThat(haptics.plays(Haptic.ALERT)).containsExactlyElementsIn(cycles).inOrder()
    }

    @Test
    fun withTheSwitchOffASilentReportUnderDoNotDisturbStaysVisual() {
        settings = AlertSettings(preAlert = AlertSound.TICK, ringUnderDnd = false)
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)

        player.playPreAlert()
        scheduler.advanceBy(5_000L)
        player.startAlert()
        scheduler.advanceBy(60_000L)

        assertThat(sound.events).isEmpty()
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun everyCycleAppliesTheSwitchToTheConditionsItReadsThen() {
        // DND turned on during an alert: with the switch on the next cycles ring on …
        ringer(RingerMode.NORMAL)
        player.startAlert()
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)
        scheduler.advanceBy(60_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L, 3_500L, 7_000L, 10_500L).inOrder()

        // … with it off the next cycle falls silent, as for a silent report before D-099.
        settings = AlertSettings(preAlert = AlertSound.TICK, ringUnderDnd = false)
        ringer(RingerMode.NORMAL)
        player.startAlert()
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)
        scheduler.advanceBy(60_000L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE).filter { it >= 60_000L }).containsExactly(60_000L)
        assertThat(haptics.plays(Haptic.ALERT).filter { it >= 60_000L }).containsExactly(60_000L)
        assertThat(sound.events).containsAtLeast("stop @ 63500", "abandonFocus @ 63500").inOrder()
    }

    @Test
    fun alarmTestenFollowsTheSwitchUnderDoNotDisturb() {
        // SET-31 plays through the same matrix: it is how the PO hears the setting.
        environment.conditions = AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY)
        settings = AlertSettings(length = AlertLength.SHORT, preAlert = AlertSound.TICK)
        player.testAlert()
        scheduler.advanceBy(10_000L)
        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(1_500L)

        settings = AlertSettings(length = AlertLength.SHORT, preAlert = AlertSound.TICK, ringUnderDnd = false)
        player.testAlert()
        scheduler.advanceBy(10_000L)
        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(1_500L)
    }

    @Test
    fun theSwitchIsReadFromTheStoredSettingsWithDefaultOn() {
        assertThat(AlertSettings.of(UserSettings()).ringUnderDnd).isTrue()
        assertThat(AlertSettings.of(UserSettings(ringUnderDnd = false)).ringUnderDnd).isFalse()
    }

    @Test
    fun vibrationSettingOffKeepsOnlyTheToneInNormalMode() {
        settings = AlertSettings(vibration = false)
        ringer(RingerMode.NORMAL)

        player.startAlert()

        assertThat(sound.plays(AlertSound.KLANGSCHALE)).containsExactly(0L)
        assertThat(haptics.events).isEmpty()
    }

    // --- Lautstärke (SET-27, D-098) ------------------------------------------

    @Test
    fun theTickAndEveryAlertCyclePlayAtTheLautstaerkeAsGain() {
        listOf(10 to 0.1f, 50 to 0.5f, 100 to 1f).forEach { (percent, gain) ->
            val fresh = FreshPlayer(
                AlertConditions(RingerMode.NORMAL),
                AlertSettings(preAlert = AlertSound.TICK, volumePercent = percent),
            )

            fresh.player.playPreAlert()
            fresh.scheduler.advanceBy(5_000L)
            fresh.player.startAlert()
            fresh.scheduler.advanceBy(60_000L)

            assertWithMessage("$percent %").that(fresh.sound.gains).containsExactly(
                "TICK $gain @ 0",
                "KLANGSCHALE $gain @ 5000",
                "KLANGSCHALE $gain @ 8500",
                "KLANGSCHALE $gain @ 12000",
                "KLANGSCHALE $gain @ 15500",
            ).inOrder()
        }
    }

    @Test
    fun alarmTestenPlaysAtTheLautstaerke() {
        settings = AlertSettings(AlertSound.GLOCKE, AlertLength.SHORT, preAlert = AlertSound.TICK, volumePercent = 30)

        player.testAlert()
        scheduler.advanceBy(60_000L)

        assertThat(sound.gains).containsExactly("TICK 0.3 @ 0", "GLOCKE 0.3 @ 1500").inOrder()
    }

    @Test
    fun aLautstaerkeOutsideTenToHundredIsClampedSoTheAppNeverSilencesACue() {
        // 05 §6: a stored value outside 10…100 (a damaged or hand-edited file) is clamped (rule 8).
        assertThat(AlertSettings.of(UserSettings(alertVolumePercent = 0)).volumePercent).isEqualTo(10)
        assertThat(AlertSettings.of(UserSettings(alertVolumePercent = -40)).volumePercent).isEqualTo(10)
        assertThat(AlertSettings.of(UserSettings(alertVolumePercent = 250)).volumePercent).isEqualTo(100)
        assertThat(AlertSettings.of(UserSettings(alertVolumePercent = 70)).volumePercent).isEqualTo(70)
        assertThat(AlertSettings.of(UserSettings()).gain).isEqualTo(1f)
        assertThat(AlertSettings(volumePercent = 0).gain).isEqualTo(0.1f)
    }

    @Test
    fun matrixTable() {
        fun outputs(mode: RingerMode, inCall: Boolean = false, dnd: DoNotDisturb = DoNotDisturb.OFF) =
            AlertMatrix.outputs(AlertConditions(mode, inCall, dnd))

        assertThat(outputs(RingerMode.NORMAL)).isEqualTo(AlertOutputs(sound = true, vibration = true))
        assertThat(outputs(RingerMode.VIBRATE)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.SILENT)).isEqualTo(AlertOutputs.NONE)
        assertThat(outputs(RingerMode.NORMAL, inCall = true)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.VIBRATE, inCall = true)).isEqualTo(AlertOutputs(sound = false, vibration = true))
        assertThat(outputs(RingerMode.SILENT, inCall = true)).isEqualTo(AlertOutputs.NONE)
        RingerMode.entries.forEach {
            assertThat(outputs(it, dnd = DoNotDisturb.TOTAL_SILENCE)).isEqualTo(AlertOutputs.NONE)
        }
    }

    @Test
    fun matrixTableUnderDoNotDisturb() {
        fun outputs(mode: RingerMode, dnd: DoNotDisturb, inCall: Boolean = false, ringUnderDnd: Boolean = true) =
            AlertMatrix.outputs(AlertConditions(mode, inCall, dnd), ringUnderDnd = ringUnderDnd)
        val soundAndVibration = AlertOutputs(sound = true, vibration = true)
        val vibrationOnly = AlertOutputs(sound = false, vibration = true)
        val switchStates = listOf(true, false)

        // Priority mode letting alarms through (One UI's DND): as the ringer mode (04 §9.1), except a silent
        // report, which One UI gives under DND whatever the user chose (D-099).
        switchStates.forEach { on ->
            fun priority(mode: RingerMode, inCall: Boolean = false) =
                outputs(mode, DoNotDisturb.PRIORITY, inCall, ringUnderDnd = on)
            assertWithMessage("SET-32 $on").that(priority(RingerMode.NORMAL)).isEqualTo(soundAndVibration)
            assertWithMessage("SET-32 $on").that(priority(RingerMode.VIBRATE)).isEqualTo(vibrationOnly)
            assertWithMessage("SET-32 $on").that(priority(RingerMode.NORMAL, inCall = true)).isEqualTo(vibrationOnly)
        }
        // „Bei „Nicht stören" klingeln" (SET-32) on, the default: a silent report rings as the ringer on normal
        // would; off: the reported ringer mode decides, as in v0.3.0-rc.1.
        assertThat(outputs(RingerMode.SILENT, DoNotDisturb.PRIORITY)).isEqualTo(soundAndVibration)
        assertThat(outputs(RingerMode.SILENT, DoNotDisturb.PRIORITY, ringUnderDnd = false)).isEqualTo(AlertOutputs.NONE)
        // SET-11 off: the sound alone. In a call: vibration only, as with the ringer on normal.
        assertThat(
            AlertMatrix.outputs(
                AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY),
                vibrationEnabled = false,
                ringUnderDnd = true,
            ),
        ).isEqualTo(AlertOutputs(sound = true, vibration = false))
        assertThat(outputs(RingerMode.SILENT, DoNotDisturb.PRIORITY, inCall = true)).isEqualTo(vibrationOnly)
        assertThat(outputs(RingerMode.SILENT, DoNotDisturb.PRIORITY, inCall = true, ringUnderDnd = false))
            .isEqualTo(AlertOutputs.NONE)

        // Priority mode whose policy blocks alarms: the ringer mode as reported, whatever the switch.
        switchStates.forEach { on ->
            fun blocked(mode: RingerMode) = outputs(mode, DoNotDisturb.PRIORITY_WITHOUT_ALARMS, ringUnderDnd = on)
            assertWithMessage("SET-32 $on").that(blocked(RingerMode.NORMAL)).isEqualTo(soundAndVibration)
            assertWithMessage("SET-32 $on").that(blocked(RingerMode.VIBRATE)).isEqualTo(vibrationOnly)
            assertWithMessage("SET-32 $on").that(blocked(RingerMode.SILENT)).isEqualTo(AlertOutputs.NONE)
        }

        // „Alarms only": as the ringer mode; the platform reports the ringer as silent in this mode (AOSP's zen
        // forces it), so a silent report here is not the user's silent mode and the alert rings, with the switch
        // on or off (D-098 decision 8, kept by D-099).
        switchStates.forEach { on ->
            fun alarmsOnly(mode: RingerMode, inCall: Boolean = false) =
                outputs(mode, DoNotDisturb.ALARMS_ONLY, inCall, ringUnderDnd = on)
            assertWithMessage("SET-32 $on").that(alarmsOnly(RingerMode.NORMAL)).isEqualTo(soundAndVibration)
            assertWithMessage("SET-32 $on").that(alarmsOnly(RingerMode.VIBRATE)).isEqualTo(vibrationOnly)
            assertWithMessage("SET-32 $on").that(alarmsOnly(RingerMode.SILENT)).isEqualTo(soundAndVibration)
            assertWithMessage("SET-32 $on").that(alarmsOnly(RingerMode.SILENT, inCall = true)).isEqualTo(vibrationOnly)
        }

        // Total silence: visual only, whatever the ringer mode and the switch.
        RingerMode.entries.forEach { mode ->
            switchStates.forEach { on ->
                assertWithMessage("$mode, SET-32 $on")
                    .that(outputs(mode, DoNotDisturb.TOTAL_SILENCE, ringUnderDnd = on))
                    .isEqualTo(AlertOutputs.NONE)
            }
        }

        // DND off: a silent report is the user's own silent mode, whatever the switch.
        switchStates.forEach { on ->
            assertWithMessage("SET-32 $on").that(outputs(RingerMode.SILENT, DoNotDisturb.OFF, ringUnderDnd = on))
                .isEqualTo(AlertOutputs.NONE)
        }
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
        scheduler.advanceBy(2_000L)
        assertThat(sound.focusHeld).isFalse()
        assertThat(sound.plays(AlertSound.TICK)).hasSize(1)
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun preAlertPlaysTheChosenSoundAndLetsItRingOutBeforeReleasingTheFocus() {
        // SET-26: the Vorwarnton rings for about 3 s (04 §9.2); nothing may cut it short.
        ringer(RingerMode.NORMAL)
        settings = AlertSettings(preAlert = AlertSound.KLANGSTAB)

        player.playPreAlert()
        assertThat(sound.events).containsExactly("requestFocus @ 0", "play KLANGSTAB @ 0").inOrder()

        scheduler.advanceBy(3_200L)
        assertThat(sound.playing).isEqualTo(AlertSound.KLANGSTAB)
        assertThat(sound.focusHeld).isTrue()

        scheduler.advanceBy(1_000L)
        assertThat(sound.focusHeld).isFalse()
        assertThat(sound.plays(AlertSound.KLANGSTAB)).containsExactly(0L)
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun theAlertCutsAPreAlertSoundThatStillRings() {
        // Rule 8: a pre-alert played late (≥ 1 s before the deadline) never delays the alert.
        ringer(RingerMode.NORMAL)
        settings = AlertSettings(preAlert = AlertSound.TIEFE_SCHALE)
        player.playPreAlert()
        scheduler.advanceBy(1_000L)

        player.startAlert()

        assertThat(sound.events).containsAtLeast("stop @ 1000", "play KLANGSCHALE @ 1000").inOrder()
        assertThat(sound.playing).isEqualTo(AlertSound.KLANGSCHALE)
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

    // --- Every tone × alert length × condition (SET-09, SET-14, #44) ----------

    @Test
    fun everyToneAndLengthFollowsTheMatrixAndReportsTheEndOnce() {
        AlertSound.TONES.forEach { tone ->
            AlertLength.entries.forEach { length ->
                MATRIX.forEach { case ->
                    val run = FreshPlayer(
                        case.conditions,
                        AlertSettings(tone, length, case.vibrationSetting, ringUnderDnd = case.ringUnderDnd),
                    )

                    run.player.startAlert(onFinished = { run.finishedAt += run.scheduler.now })
                    run.scheduler.advanceBy(300_000L)

                    val label = "$tone $length ${case.name}"
                    val cycles = CYCLE_STARTS.getValue(tone to length)
                    assertWithMessage("$label: tone").that(run.sound.plays(tone))
                        .isEqualTo(if (case.sound) cycles else emptyList())
                    assertWithMessage("$label: vibration").that(run.haptics.plays(Haptic.ALERT))
                        .isEqualTo(if (case.vibration) cycles else emptyList())
                    assertWithMessage("$label: end reported").that(run.finishedAt)
                        .containsExactly(ALERT_END_MS.getValue(tone to length))
                    assertWithMessage("$label: released").that(run.sound.focusHeld).isFalse()
                    assertWithMessage("$label: nothing pending").that(run.scheduler.pendingCount).isEqualTo(0)
                }
            }
        }
    }

    @Test
    fun aStoppedAlertReportsNoEnd() {
        val finished = mutableListOf<Long>()
        player.startAlert(onFinished = { finished += scheduler.now })
        scheduler.advanceBy(5_000L)

        player.stopAlert()
        scheduler.advanceBy(300_000L)

        assertThat(finished).isEmpty()
    }

    @Test
    fun aReplacedAlertReportsOnlyTheEndOfTheNewOne() {
        val finished = mutableListOf<String>()
        player.startAlert(onFinished = { finished += "first @ ${scheduler.now}" })
        scheduler.advanceBy(5_000L)

        player.startAlert(onFinished = { finished += "second @ ${scheduler.now}" })
        scheduler.advanceBy(300_000L)

        assertThat(finished).containsExactly("second @ 19000")
    }

    @Test
    fun aPreAlertDuringTheAlertNeverCutsTheAlert() {
        ringer(RingerMode.NORMAL)
        val finished = mutableListOf<Long>()
        player.startAlert(onFinished = { finished += scheduler.now })
        scheduler.advanceBy(1_000L)

        player.playPreAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.TICK)).isEmpty()
        assertThat(haptics.plays(Haptic.PRE_ALERT)).isEmpty()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(finished).containsExactly(14_000L)
    }

    // --- Alarm testen (SET-31) -----------------------------------------------

    @Test
    fun testAlertPlaysTheTickThenTheAlertWithTheCurrentSettings() {
        ringer(RingerMode.NORMAL)
        settings = AlertSettings(tone = AlertSound.GLOCKE, length = AlertLength.NORMAL, preAlert = AlertSound.TICK)

        player.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.PRE_ALERT)).containsExactly(0L)
        val starts = listOf(1_500L, 4_500L, 7_500L, 10_500L, 13_500L)
        assertThat(sound.plays(AlertSound.GLOCKE)).isEqualTo(starts)
        assertThat(haptics.plays(Haptic.ALERT)).isEqualTo(starts)
        assertThat(sound.focusHeld).isFalse()
        assertThat(scheduler.pendingCount).isEqualTo(0)
    }

    @Test
    fun testAlertLetsTheChosenPreAlertSoundRingOutThenPausesBeforeTheAlert() {
        // SET-31 with SET-26: the PO judges the Vorwarnton's fall-off, so the alert waits for its
        // end plus the pause of 0.7 s (the tick's 0.8 s + 0.7 s = 1.5 s, D-059).
        ringer(RingerMode.NORMAL)
        settings = AlertSettings(tone = AlertSound.GLOCKE, length = AlertLength.SHORT, preAlert = AlertSound.ZIMBEL)

        player.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.events.filter { it.startsWith("play") || it.startsWith("stop") })
            .containsExactly("play ZIMBEL @ 0", "play GLOCKE @ 3900", "stop @ 6900").inOrder()
        assertThat(haptics.plays(Haptic.ALERT)).containsExactly(3_900L)
    }

    @Test
    fun testAlertFollowsTheRingerModeLikeARealAlert() {
        ringer(RingerMode.VIBRATE)
        settings = AlertSettings(tone = AlertSound.SANFT, length = AlertLength.SHORT, preAlert = AlertSound.TICK)

        player.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.events).isEmpty()
        assertThat(haptics.plays(Haptic.PRE_ALERT)).containsExactly(0L)
        assertThat(haptics.plays(Haptic.ALERT)).containsExactly(1_500L)
    }

    @Test
    fun testAlertIsIgnoredWhileARealAlertOrTickPlays() {
        ringer(RingerMode.NORMAL)
        val finished = mutableListOf<Long>()
        player.startAlert(onFinished = { finished += scheduler.now })

        player.testAlert()
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.TICK)).isEmpty()
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
        assertThat(finished).containsExactly(14_000L)

        player.playPreAlert()
        player.testAlert()
        scheduler.advanceBy(300_000L)
        assertThat(sound.plays(AlertSound.TICK)).hasSize(1)
        assertThat(sound.plays(AlertSound.KLANGSCHALE)).hasSize(4)
    }

    @Test
    fun aRealAlertOrTickReplacesARunningTest() {
        ringer(RingerMode.NORMAL)
        settings = AlertSettings(tone = AlertSound.HOLZBLOCK, preAlert = AlertSound.TICK)
        player.testAlert()
        scheduler.advanceBy(2_000L)

        val finished = mutableListOf<Long>()
        player.startAlert(onFinished = { finished += scheduler.now })
        scheduler.advanceBy(300_000L)

        assertThat(sound.plays(AlertSound.HOLZBLOCK)).containsExactly(1_500L, 2_000L, 5_000L, 8_000L, 11_000L, 14_000L)
        assertThat(finished).containsExactly(17_000L)

        player.testAlert()
        scheduler.advanceBy(1_000L)
        player.playPreAlert()
        scheduler.advanceBy(300_000L)
        // The first test's tick at 0, the second test's at 302 s, then the real tick that ends it.
        assertThat(sound.plays(AlertSound.TICK)).containsExactly(0L, 302_000L, 303_000L).inOrder()
        assertThat(sound.plays(AlertSound.HOLZBLOCK)).hasSize(6)
    }

    @Test
    fun stopTestAlertStopsOnlyATest() {
        ringer(RingerMode.NORMAL)
        player.testAlert()
        scheduler.advanceBy(2_000L)

        player.stopTestAlert()

        assertThat(sound.playing).isNull()
        assertThat(sound.focusHeld).isFalse()
        assertThat(haptics.vibrating).isNull()
        assertThat(scheduler.pendingCount).isEqualTo(0)

        val finished = mutableListOf<Long>()
        player.startAlert(onFinished = { finished += scheduler.now })
        player.stopTestAlert()
        scheduler.advanceBy(300_000L)
        assertThat(finished).containsExactly(16_000L)
    }

    private fun ringer(mode: RingerMode) {
        environment.conditions = AlertConditions(mode)
    }

    /** A player with its own fakes, for the table test. */
    private class FreshPlayer(conditions: AlertConditions, settings: AlertSettings) {
        val scheduler = FakeScheduler()
        val sound = FakeSoundOutput(scheduler)
        val haptics = FakeHaptics(scheduler)
        val finishedAt = mutableListOf<Long>()
        val player = RingerModeAlertPlayer(FakeEnvironment(conditions), sound, haptics, scheduler) { settings }
    }

    /** One row of the 04 §9.1 matrix: the conditions, the SET-11 setting and what the alert must use. */
    private data class MatrixCase(
        val name: String,
        val conditions: AlertConditions,
        val vibrationSetting: Boolean,
        val sound: Boolean,
        val vibration: Boolean,
        /** SET-32 „Bei „Nicht stören" klingeln" (D-099). */
        val ringUnderDnd: Boolean = true,
    )

    private companion object {
        val MATRIX = listOf(
            MatrixCase("normal", AlertConditions(RingerMode.NORMAL), true, sound = true, vibration = true),
            MatrixCase(
                "normal, SET-11 off",
                AlertConditions(RingerMode.NORMAL),
                false,
                sound = true,
                vibration = false,
            ),
            MatrixCase("vibrate", AlertConditions(RingerMode.VIBRATE), true, sound = false, vibration = true),
            MatrixCase("silent", AlertConditions(RingerMode.SILENT), true, sound = false, vibration = false),
            MatrixCase(
                "normal, in a call",
                AlertConditions(RingerMode.NORMAL, inCall = true),
                true,
                sound = false,
                vibration = true,
            ),
            MatrixCase(
                "vibrate, in a call",
                AlertConditions(RingerMode.VIBRATE, inCall = true),
                true,
                sound = false,
                vibration = true,
            ),
            MatrixCase(
                "silent, in a call",
                AlertConditions(RingerMode.SILENT, inCall = true),
                true,
                sound = false,
                vibration = false,
            ),
            MatrixCase(
                "normal, DND total silence",
                AlertConditions(RingerMode.NORMAL, dnd = DoNotDisturb.TOTAL_SILENCE),
                true,
                sound = false,
                vibration = false,
            ),
            MatrixCase(
                "vibrate, DND total silence",
                AlertConditions(RingerMode.VIBRATE, dnd = DoNotDisturb.TOTAL_SILENCE),
                true,
                sound = false,
                vibration = false,
            ),
            MatrixCase(
                "normal, DND alarms only",
                AlertConditions(RingerMode.NORMAL, dnd = DoNotDisturb.ALARMS_ONLY),
                true,
                sound = true,
                vibration = true,
            ),
            MatrixCase(
                "vibrate, DND priority",
                AlertConditions(RingerMode.VIBRATE, dnd = DoNotDisturb.PRIORITY),
                true,
                sound = false,
                vibration = true,
            ),
            MatrixCase(
                "silent, DND priority, SET-32 on",
                AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY),
                true,
                sound = true,
                vibration = true,
            ),
            MatrixCase(
                "silent, DND priority, SET-32 on, SET-11 off",
                AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY),
                false,
                sound = true,
                vibration = false,
            ),
            MatrixCase(
                "silent, DND priority, SET-32 off",
                AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY),
                true,
                sound = false,
                vibration = false,
                ringUnderDnd = false,
            ),
            MatrixCase(
                "silent, DND priority without alarms, SET-32 on",
                AlertConditions(RingerMode.SILENT, dnd = DoNotDisturb.PRIORITY_WITHOUT_ALARMS),
                true,
                sound = false,
                vibration = false,
            ),
        )

        /**
         * 04 §9.1: a cycle is the tone, at least the vibration pattern (2 s) plus a
         * 1 s pause: 3.5 s for the Klangschale, 3 s for the others. Normal: cycles
         * that end within 15 s; Lang: within 2 min; Kurz: one.
         */
        private fun starts(cycleMs: Long, count: Int) = (0 until count).map { it * cycleMs }

        val CYCLE_STARTS: Map<Pair<AlertSound, AlertLength>, List<Long>> = buildMap {
            put(AlertSound.KLANGSCHALE to AlertLength.SHORT, listOf(0L))
            put(AlertSound.KLANGSCHALE to AlertLength.NORMAL, listOf(0L, 3_500L, 7_000L, 10_500L))
            put(AlertSound.KLANGSCHALE to AlertLength.LONG, starts(3_500L, 34))
            listOf(AlertSound.GLOCKE, AlertSound.HOLZBLOCK, AlertSound.GLOCKENSPIEL, AlertSound.SANFT).forEach {
                put(it to AlertLength.SHORT, listOf(0L))
                put(it to AlertLength.NORMAL, listOf(0L, 3_000L, 6_000L, 9_000L, 12_000L))
                put(it to AlertLength.LONG, starts(3_000L, 40))
            }
        }

        val ALERT_END_MS: Map<Pair<AlertSound, AlertLength>, Long> = buildMap {
            put(AlertSound.KLANGSCHALE to AlertLength.SHORT, 3_500L)
            put(AlertSound.KLANGSCHALE to AlertLength.NORMAL, 14_000L)
            put(AlertSound.KLANGSCHALE to AlertLength.LONG, 119_000L)
            listOf(AlertSound.GLOCKE, AlertSound.HOLZBLOCK, AlertSound.GLOCKENSPIEL, AlertSound.SANFT).forEach {
                put(it to AlertLength.SHORT, 3_000L)
                put(it to AlertLength.NORMAL, 15_000L)
                put(it to AlertLength.LONG, 120_000L)
            }
        }
    }
}
