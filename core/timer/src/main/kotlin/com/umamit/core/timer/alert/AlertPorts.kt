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

/** Plays bundled sounds with notification-event audio attributes and holds the audio focus. */
interface AlertSoundOutput {
    /**
     * Requests transient audio focus with ducking; true when granted.
     * [onLost] runs when another app takes the focus away for longer.
     */
    fun requestFocus(onLost: () -> Unit): Boolean

    fun abandonFocus()

    /**
     * Loads [sounds] now, so that a later [play] of one of them only starts it, and releases the
     * loaded sounds not in [sounds] (empty: all of them; D-092). A sound that [play] used is
     * loaded again after it, while it is still wanted. It blocks its thread while it loads, so
     * the player calls it only after the cue at hand has started; a sound that does not load is
     * left out, and [play] loads it as before. Must not throw.
     */
    fun prepare(sounds: Set<AlertSound>)

    /** The sounds that are loaded now: [play] starts them without loading. */
    val ready: Set<AlertSound>

    /**
     * Plays [sound] once from the start, replacing anything playing: a loaded one is only
     * started, any other is loaded first. Must not throw.
     */
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
