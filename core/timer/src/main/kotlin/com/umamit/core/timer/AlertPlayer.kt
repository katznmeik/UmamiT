package com.umamit.core.timer

/**
 * Plays the pre-alert and the deadline alert (04 §9.1). It is called by the
 * timer service, which holds the wake lock while it plays.
 *
 * The implementation is [com.umamit.core.timer.alert.RingerModeAlertPlayer]
 * (tone + vibration in normal mode, vibration in vibrate mode, nothing in
 * silent mode; #16), built by `AlertPlayers.ringerMode(context)`.
 */
interface AlertPlayer {
    /** Soft tick and short haptic at the pre-alert (reduced form of the alert). */
    fun playPreAlert()

    /** Starts the alert at the deadline. */
    fun startAlert()

    /** Stops a playing alert (acknowledged, step stopped, service destroyed); idempotent. */
    fun stopAlert()

    /** Visual only: the full-screen alert and the Live Update carry the alert. */
    object Silent : AlertPlayer {
        override fun playPreAlert() = Unit

        override fun startAlert() = Unit

        override fun stopAlert() = Unit
    }
}
