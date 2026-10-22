package com.umamit.core.timer.alert

/** The phone's ringer mode (04 §9.1), read when an alert or pre-alert starts and on every repeat. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/**
 * What the device allows right now.
 *
 * @property inCall a call is ringing or active (incl. VoIP): vibrate only (04 §9.1).
 * @property dndBlocksAlerts Do Not Disturb is set to *total silence* or *alarms
 *   only*; the platform would mute a notification-event sound and vibration
 *   anyway, so the alert stays visual. In *priority only* mode the platform
 *   decides (it honours per-app exceptions); the player does not bypass it.
 */
data class AlertConditions(
    val ringerMode: RingerMode,
    val inCall: Boolean = false,
    val dndBlocksAlerts: Boolean = false,
)

/** Which outputs an alert (or pre-alert) uses; the visual alert always shows. */
data class AlertOutputs(val sound: Boolean, val vibration: Boolean) {
    companion object {
        val NONE = AlertOutputs(sound = false, vibration = false)
    }
}

/**
 * The ringer-mode matrix of 04 §9.1, the same for the pre-alert (tick + short
 * haptic) and the alert (tone + vibration pattern):
 *
 * | Ringer mode | Outputs |
 * |---|---|
 * | Normal | sound + vibration (vibration only if SET-11 is on) |
 * | Normal, in a call | vibration only |
 * | Vibrate | vibration |
 * | Silent, or DND blocks alerts | none (visual only) |
 */
object AlertMatrix {
    fun outputs(conditions: AlertConditions, vibrationEnabled: Boolean = true): AlertOutputs {
        if (conditions.dndBlocksAlerts) return AlertOutputs.NONE
        return when (conditions.ringerMode) {
            RingerMode.NORMAL -> if (conditions.inCall) {
                AlertOutputs(sound = false, vibration = true)
            } else {
                AlertOutputs(sound = true, vibration = vibrationEnabled)
            }

            RingerMode.VIBRATE -> AlertOutputs(sound = false, vibration = true)

            RingerMode.SILENT -> AlertOutputs.NONE
        }
    }
}

/**
 * Bundled sounds (04 §9.2), synthesised by `scripts/sounds/generate.sh` into
 * `core:content` `res/raw`. [lengthMs] is the file's length; a test checks it
 * against the Ogg file. M1-10 adds Glocke, Holzblock, Glockenspiel and Sanft.
 */
enum class AlertSound(val lengthMs: Long) {
    /** Default alert tone (draft, Q-15). */
    KLANGSCHALE(lengthMs = 3_500L),

    /** Pre-alert tick. */
    TICK(lengthMs = 90L),
}

private val PRE_ALERT_TIMINGS_MS = listOf(0L, 50L)

/** 04 §9.1: `[0, 400, 200, 400, 200, 800]` ms. */
private val ALERT_TIMINGS_MS = listOf(0L, 400L, 200L, 400L, 200L, 800L)

/** Vibration waveforms as off/on timings in ms (`VibrationEffect.createWaveform`, no repeat). */
enum class Haptic(val timings: List<Long>) {
    /** Short haptic at the pre-alert (03 §3.6 „pre-alert tick"). */
    PRE_ALERT(PRE_ALERT_TIMINGS_MS),

    /** The alert pattern of 04 §9.1. */
    ALERT(ALERT_TIMINGS_MS),
    ;

    val lengthMs: Long get() = timings.sum()
}

/**
 * SET-14 Alarmdauer (04 §9.1): Kurz = once, Normal = repeat up to 15 s,
 * Lang = repeat up to 2 min (until acknowledged; acknowledging calls
 * `stopAlert()`).
 */
enum class AlertLength(val limitMs: Long?) {
    SHORT(limitMs = null),
    NORMAL(limitMs = 15_000L),
    LONG(limitMs = 120_000L),
    ;

    /** How many tone cycles of [cycleMs] fit into the limit; at least one. */
    fun cycles(cycleMs: Long): Int {
        val limit = limitMs ?: return 1
        return (limit / cycleMs).toInt().coerceAtLeast(1)
    }
}

/** The user's alert settings (SET-11, SET-14; the tone picker comes with M1-10). The probe uses the defaults. */
data class AlertSettings(
    val tone: AlertSound = AlertSound.KLANGSCHALE,
    val length: AlertLength = AlertLength.NORMAL,
    val vibration: Boolean = true,
)
