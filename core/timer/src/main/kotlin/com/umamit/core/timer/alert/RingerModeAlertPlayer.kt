package com.umamit.core.timer.alert

import com.umamit.core.timer.AlertPlayer

/**
 * The [AlertPlayer] that follows the ringer mode (04 §9.1, [AlertMatrix]).
 *
 * - **Alert:** the tone (SET-09) plays once per cycle and the vibration
 *   pattern starts with it. A cycle lasts as long as the tone, at least the
 *   pattern plus a pause; cycles repeat until the next one would end after
 *   the alert length (SET-14; Normal: up to 15 s → 4 × 3.5 s for the
 *   Klangschale, 5 × 3 s for the others). The conditions are read again at
 *   every cycle, so switching to silent or taking a call takes effect at the
 *   next repeat. When the last cycle has run, the alert's `onFinished` runs
 *   once, whatever the outputs were (silent mode is still an alert); an alert
 *   that is stopped or replaced first never reports.
 * - **Pre-alert:** the tick and/or the short haptic once. It never cuts a
 *   playing alert: a pre-alert while an alert plays is ignored (rule 8).
 * - ***Alarm testen*** (SET-31, [testAlert]): the pre-alert, then the alert
 *   [TEST_ALERT_DELAY_MS] later, with the current settings and the same
 *   matrix. A test never interrupts a real alert or tick; a real one replaces
 *   a running test.
 * - **Audio focus:** transient with ducking while a sound plays; if it is
 *   refused or lost, this alert continues without sound (vibration only, if
 *   the matrix allows it).
 * - [stopAlert] cancels everything at once and is idempotent.
 *
 * Pure logic: it touches the device only through its seams, so the matrix is
 * unit-tested with fakes. Not thread-safe; it runs on the main thread and
 * [scheduler] runs its actions there.
 */
class RingerModeAlertPlayer(
    private val environment: AlertEnvironment,
    sound: AlertSoundOutput,
    haptics: AlertHaptics,
    private val scheduler: AlertScheduler,
    private val settings: () -> AlertSettings = { AlertSettings() },
) : AlertPlayer {
    private enum class Mode { IDLE, PRE_ALERT, ALERT, TEST }

    private val outputs = DeviceOutputs(sound, haptics)
    private var mode = Mode.IDLE
    private var current = AlertSettings()
    private var cyclesLeft = 0
    private var onFinished: (() -> Unit)? = null
    private var scheduled = false

    /** The pre-alert tick (TMR-23); ignored while an alert plays, so it never cuts one. */
    override fun playPreAlert() {
        if (mode == Mode.ALERT) return
        stopAlert()
        mode = Mode.PRE_ALERT
        current = settings()
        if (tick()) {
            val releaseAfter = maxOf(AlertSound.TICK.lengthMs, Haptic.PRE_ALERT.lengthMs) + RELEASE_MARGIN_MS
            schedule(releaseAfter) { stopAlert() }
        } else {
            mode = Mode.IDLE
        }
    }

    override fun startAlert() = startAlert(onFinished = null)

    /**
     * Starts the alert with the current settings, replacing anything playing;
     * [onFinished] runs once when its last cycle has run (not when it is
     * stopped or replaced first).
     */
    fun startAlert(onFinished: (() -> Unit)?) {
        stopAlert()
        mode = Mode.ALERT
        current = settings()
        beginCycles(onFinished)
    }

    /** *Alarm testen* (SET-31): the tick, then the alert; ignored while a real alert or tick plays. */
    fun testAlert() {
        if (mode == Mode.ALERT || mode == Mode.PRE_ALERT) return
        stopAlert()
        mode = Mode.TEST
        current = settings()
        tick()
        schedule(TEST_ALERT_DELAY_MS) { beginCycles(onFinished = null) }
    }

    /** Stops a running *Alarm testen*; a real alert or tick plays on. */
    fun stopTestAlert() {
        if (mode == Mode.TEST) stopAlert()
    }

    override fun stopAlert() {
        mode = Mode.IDLE
        cyclesLeft = 0
        onFinished = null
        if (scheduled) {
            scheduler.cancelAll()
            scheduled = false
        }
        outputs.releaseAll()
    }

    /** The tick and/or short haptic per the matrix; true when something plays. */
    private fun tick(): Boolean {
        val allowed = AlertMatrix.outputs(environment.conditions(), current.vibration)
        if (allowed.vibration) outputs.vibrate(Haptic.PRE_ALERT)
        if (allowed.sound) outputs.play(AlertSound.TICK)
        return outputs.active
    }

    private fun beginCycles(onFinished: (() -> Unit)?) {
        this.onFinished = onFinished
        // After a test's tick whose focus was refused, the alert asks again.
        outputs.soundAllowed = true
        cyclesLeft = current.length.cycles(cycleMs(current.tone))
        runCycle()
    }

    private fun runCycle() {
        cyclesLeft--
        val allowed = AlertMatrix.outputs(environment.conditions(), current.vibration)
        if (!allowed.sound || !outputs.play(current.tone)) outputs.silence()
        if (allowed.vibration) outputs.vibrate(Haptic.ALERT)
        schedule(cycleMs(current.tone)) {
            if (cyclesLeft > 0) runCycle() else finish()
        }
    }

    private fun finish() {
        val report = onFinished
        stopAlert()
        report?.invoke()
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        scheduler.schedule(delayMs) {
            scheduled = false
            action()
        }
        scheduled = true
    }

    companion object {
        /** Minimum pause between two vibration patterns when the tone is shorter than the pattern. */
        const val MIN_PAUSE_MS = 1_000L

        /** Extra time before the pre-alert releases the audio focus. */
        const val RELEASE_MARGIN_MS = 250L

        /** *Alarm testen*: the alert starts this long after the tick (0.8 s tick, 0.7 s pause). */
        const val TEST_ALERT_DELAY_MS = 1_500L

        /** Length of one alert cycle (tone + vibration) for [tone]. */
        fun cycleMs(tone: AlertSound): Long = maxOf(tone.lengthMs, Haptic.ALERT.lengthMs + MIN_PAUSE_MS)
    }
}

/**
 * The sound with its audio focus and the haptics of one player, so each is
 * started, stopped and released exactly once. Once the focus is refused or
 * lost, [soundAllowed] stays false until everything is released.
 */
private class DeviceOutputs(private val sound: AlertSoundOutput, private val haptics: AlertHaptics) {
    var soundAllowed = true
    private var focusHeld = false
    private var soundPlaying = false
    private var vibrating = false

    /** A sound or a vibration is playing. */
    val active: Boolean get() = soundPlaying || vibrating

    /** Plays [alertSound] when sound is allowed and the focus is held or granted; true when it plays. */
    fun play(alertSound: AlertSound): Boolean {
        if (!soundAllowed || !acquireFocus()) return false
        sound.play(alertSound)
        soundPlaying = true
        return true
    }

    /** Stops the sound and gives up the focus; the vibration goes on. */
    fun silence() {
        stopSound()
        releaseFocus()
    }

    fun vibrate(haptic: Haptic) {
        haptics.play(haptic)
        vibrating = true
    }

    /** Stops everything and gives up the focus; the next alert or tick may play sound again. */
    fun releaseAll() {
        stopSound()
        if (vibrating) {
            haptics.cancel()
            vibrating = false
        }
        releaseFocus()
        soundAllowed = true
    }

    private fun acquireFocus(): Boolean {
        if (!focusHeld) {
            focusHeld = sound.requestFocus(onLost = ::onFocusLost)
            // Refused (e.g. a call the call-state check missed): no sound for this alert.
            if (!focusHeld) soundAllowed = false
        }
        return focusHeld
    }

    private fun onFocusLost() {
        if (!focusHeld) return
        soundAllowed = false
        silence()
    }

    private fun releaseFocus() {
        if (focusHeld) {
            sound.abandonFocus()
            focusHeld = false
        }
    }

    private fun stopSound() {
        if (soundPlaying) {
            sound.stop()
            soundPlaying = false
        }
    }
}
