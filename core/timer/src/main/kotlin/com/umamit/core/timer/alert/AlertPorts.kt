package com.umamit.core.timer.alert

/*
 * The seams of RingerModeAlertPlayer. The Android adapters (AndroidAlertOutputs.kt)
 * are thin; the tests use fakes. All calls happen on the main thread.
 */

/** Reads the ringer mode, call state and Do Not Disturb (AudioManager, NotificationManager). */
fun interface AlertEnvironment {
    fun conditions(): AlertConditions
}

/** Plays bundled sounds with notification-event audio attributes and holds the audio focus. */
interface AlertSoundOutput {
    /**
     * Requests transient audio focus with ducking; true when granted.
     * [onLost] runs when another app takes the focus away for longer.
     */
    fun requestFocus(onLost: () -> Unit): Boolean

    fun abandonFocus()

    /** Plays [sound] once from the start, replacing anything playing. Must not throw. */
    fun play(sound: AlertSound)

    /** Stops and releases the playing sound, if any. */
    fun stop()
}

/** Vibrates with notification vibration attributes. */
interface AlertHaptics {
    fun play(haptic: Haptic)

    fun cancel()
}

/** Delayed actions for the repeats; [cancelAll] drops every pending one. */
interface AlertScheduler {
    fun schedule(delayMs: Long, action: () -> Unit)

    fun cancelAll()
}
