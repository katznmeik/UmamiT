package com.umamit.core.timer.alert

/*
 * The seams of RingerModeAlertPlayer. The Android adapters (AndroidAlertOutputs.kt)
 * are thin; the tests use fakes. All calls of one player happen on its thread: the alert
 * thread for the timer's player (D-092), the main thread for the tone preview.
 */

/** Reads the ringer mode, call state and Do Not Disturb (AudioManager, NotificationManager). */
fun interface AlertEnvironment {
    fun conditions(): AlertConditions
}

/** Plays bundled sounds with alarm audio attributes (D-098) and holds the audio focus. */
interface AlertSoundOutput {
    /**
     * Requests transient audio focus with ducking; true when granted.
     * [onLost] runs when another app takes the focus away for longer.
     */
    fun requestFocus(onLost: () -> Unit): Boolean

    fun abandonFocus()

    /**
     * Has [sounds] loaded ahead, so that a later [play] of one of them only starts it, and releases
     * the loaded sounds not in [sounds] (empty: all of them; D-092). A sound that [play] used is
     * loaded again after it, while it is still wanted. It returns at once: the loads run off the
     * player's thread, so a cue never waits behind one (rule 8, D-096). A sound that does not load,
     * or has not loaded yet, is not [ready], and [play] loads it on the spot as before. Must not throw.
     */
    fun prepare(sounds: Set<AlertSound>)

    /** The sounds that are loaded now: [play] starts them without loading. */
    val ready: Set<AlertSound>

    /**
     * Plays [sound] once from the start at [gain] (the player's volume on top of the alarm stream,
     * 0.1–1, SET-27), replacing anything playing: a loaded one is only started, any other is loaded
     * first. Must not throw.
     */
    fun play(sound: AlertSound, gain: Float)

    /** Stops and releases the playing sound, if any. */
    fun stop()
}

/** Vibrates with alarm vibration attributes (D-098). */
interface AlertHaptics {
    fun play(haptic: Haptic)

    fun cancel()
}

/** Delayed actions for the repeats; [cancelAll] drops every pending one. */
interface AlertScheduler {
    fun schedule(delayMs: Long, action: () -> Unit)

    fun cancelAll()
}
