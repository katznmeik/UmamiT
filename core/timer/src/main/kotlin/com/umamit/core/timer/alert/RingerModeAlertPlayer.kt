package com.umamit.core.timer.alert

import com.umamit.core.timer.AlertPlayer

/**
 * The [AlertPlayer] that follows the ringer mode (04 §9.1, [AlertMatrix]).
 *
 * - **Alert:** the tone plays once per cycle and the vibration pattern starts
 *   with it. A cycle lasts as long as the tone (at least the pattern plus a
 *   pause); cycles repeat until the next one would end after the alert
 *   length (Normal: up to 15 s → 4 × 3.5 s). The conditions are read again at
 *   every cycle, so switching to silent or taking a call takes effect at the
 *   next repeat.
 * - **Pre-alert:** the tick and/or the short haptic once.
 * - **Audio focus:** transient with ducking while a sound plays; if it is
 *   refused or lost, this alert continues without sound (vibration only, if
 *   the matrix allows it).
 * - [stopAlert] cancels everything at once and is idempotent.
 *
 * Pure logic: it touches the device only through its seams, so the matrix is
 * unit-tested with fakes. Not thread-safe; the timer service calls it on the
 * main thread and [scheduler] runs its actions there.
 */
class RingerModeAlertPlayer(
    private val environment: AlertEnvironment,
    private val sound: AlertSoundOutput,
    private val haptics: AlertHaptics,
    private val scheduler: AlertScheduler,
    private val settings: () -> AlertSettings = { AlertSettings() },
) : AlertPlayer {
    private var current = AlertSettings()
    private var cyclesLeft = 0
    private var soundAllowed = true
    private var focusHeld = false
    private var soundPlaying = false
    private var vibrating = false
    private var scheduled = false

    override fun playPreAlert() {
        stopAlert()
        current = settings()
        val outputs = AlertMatrix.outputs(environment.conditions(), current.vibration)
        if (outputs.vibration) vibrate(Haptic.PRE_ALERT)
        if (outputs.sound && acquireFocus()) playSound(AlertSound.TICK)
        if (soundPlaying || vibrating) {
            val releaseAfter = maxOf(AlertSound.TICK.lengthMs, Haptic.PRE_ALERT.lengthMs) + RELEASE_MARGIN_MS
            schedule(releaseAfter) { stopAlert() }
        }
    }

    override fun startAlert() {
        stopAlert()
        current = settings()
        soundAllowed = true
        cyclesLeft = current.length.cycles(cycleMs(current.tone))
        runCycle()
    }

    override fun stopAlert() {
        cyclesLeft = 0
        if (scheduled) {
            scheduler.cancelAll()
            scheduled = false
        }
        stopSound()
        if (vibrating) {
            haptics.cancel()
            vibrating = false
        }
        releaseFocus()
    }

    private fun runCycle() {
        cyclesLeft--
        val outputs = AlertMatrix.outputs(environment.conditions(), current.vibration)
        if (outputs.sound && soundAllowed && acquireFocus()) {
            playSound(current.tone)
        } else {
            stopSound()
            releaseFocus()
        }
        if (outputs.vibration) vibrate(Haptic.ALERT)
        schedule(cycleMs(current.tone)) {
            if (cyclesLeft > 0) runCycle() else stopAlert()
        }
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
        stopSound()
        releaseFocus()
    }

    private fun releaseFocus() {
        if (focusHeld) {
            sound.abandonFocus()
            focusHeld = false
        }
    }

    private fun playSound(alertSound: AlertSound) {
        sound.play(alertSound)
        soundPlaying = true
    }

    private fun stopSound() {
        if (soundPlaying) {
            sound.stop()
            soundPlaying = false
        }
    }

    private fun vibrate(haptic: Haptic) {
        haptics.play(haptic)
        vibrating = true
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

        /** Length of one alert cycle (tone + vibration) for [tone]. */
        fun cycleMs(tone: AlertSound): Long = maxOf(tone.lengthMs, Haptic.ALERT.lengthMs + MIN_PAUSE_MS)
    }
}
