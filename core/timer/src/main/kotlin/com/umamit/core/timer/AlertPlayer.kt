package com.umamit.core.timer

/**
 * Plays the pre-alert and the deadline alert (04 §9.1). It is called by the
 * timer service, which holds the wake lock while it plays.
 *
 * The probe (M1-03a, #15) is visual only and uses [Silent]; #16 plugs in the
 * ringer-mode implementation (tone + vibration in normal mode, vibration in
 * vibrate mode, nothing in silent mode) in `ProbeTimerService.createAlertPlayer()`.
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
