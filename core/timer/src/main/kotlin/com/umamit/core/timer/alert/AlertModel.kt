package com.umamit.core.timer.alert

import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings

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

/** What the player starts, for the diagnostics log (06 §5.6): the pre-alert tick, a step's alert, *Alarm testen*. */
enum class AlertCue { PRE_ALERT, ALERT, TEST }

/**
 * Where the start of a step's tick or alert spent its time, for the diagnostics log (06 §5.6,
 * D-092): [queuedMs] from the controller's call to the start of its command on the alert
 * thread, [settingsMs] waiting for the settings file, [outputsMs] starting the outputs (the
 * ringer matrix, the vibration, the audio focus and the sound). [soundLoaded]: the sound was
 * loaded before the cue (true) or by it (false); null when no sound played.
 */
data class CueStart(val queuedMs: Long, val settingsMs: Long, val outputsMs: Long, val soundLoaded: Boolean?)

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
 * `core:content` `res/raw`. [key] is the file name; for a tone it is also the
 * stored value of the `alertSound` setting (05 §6, SET-09). [lengthMs] is the
 * file's length, which a test checks against the Ogg file. The pre-alert
 * sounds are chosen through [PreAlertSound] (SET-26).
 */
enum class AlertSound(val key: String, val lengthMs: Long) {
    /** Default alert tone, a bright singing bowl (Q-15: variant C chosen by the PO, D-045). */
    KLANGSCHALE(key = "klangschale", lengthMs = 3_500L),

    /** A small bell struck twice (D-059). */
    GLOCKE(key = "glocke", lengthMs = 3_000L),

    /** Two wood blocks knocked twice, „tok-tok … tok-tok" (D-059). */
    HOLZBLOCK(key = "holzblock", lengthMs = 1_200L),

    /** A rising four-note steel-bar arpeggio (D-059). */
    GLOCKENSPIEL(key = "glockenspiel", lengthMs = 2_200L),

    /** Two soft, round tones rising a fourth (D-059). */
    SANFT(key = "sanft", lengthMs = 3_000L),

    /** Pre-alert tick, a soft small-bowl „ting" (Q-15: variant C, D-045); „Ting (leise)" in SET-26. */
    TICK(key = "tick", lengthMs = 800L),

    /** Pre-alert: two small cymbals (tingsha), bright, slow beating (SET-26, D-093). */
    ZIMBEL(key = "zimbel", lengthMs = 3_200L),

    /** Pre-alert: a small high bell, 1320 Hz, soft strike (SET-26, D-093). */
    GLOECKCHEN(key = "gloeckchen", lengthMs = 3_000L),

    /** Pre-alert: one wind-chime bar, pure partials, long ring (SET-26, D-093). */
    KLANGSTAB(key = "klangstab", lengthMs = 3_200L),

    /** Pre-alert: a lower singing bowl, 440 Hz, round (SET-26, D-093). */
    TIEFE_SCHALE(key = "tiefe_schale", lengthMs = 3_200L),
    ;

    companion object {
        /** The SET-09 choices in the settings' order, the default first. */
        val TONES: List<AlertSound> = listOf(KLANGSCHALE, GLOCKE, HOLZBLOCK, GLOCKENSPIEL, SANFT)

        /** The tone stored under [key]; an unknown key (or a pre-alert sound's) is the default, the Klangschale. */
        fun tone(key: String): AlertSound = TONES.firstOrNull { it.key == key } ?: KLANGSCHALE
    }
}

/**
 * The SET-26 *Vorwarnton* choices (04 §9.2, D-093) in the settings' order: [key] is the stored
 * value of the `preAlertSound` setting (05 §6), [sound] the bundled file the pre-alert plays.
 * Another sound, e.g. the PO's own recording, is one more entry here with its [AlertSound]; its
 * file must stay shorter than the 5 s between a pre-alert and its deadline (E7).
 */
enum class PreAlertSound(val key: String, val sound: AlertSound) {
    ZIMBEL(key = "zimbel", sound = AlertSound.ZIMBEL),
    GLOECKCHEN(key = "gloeckchen", sound = AlertSound.GLOECKCHEN),
    KLANGSTAB(key = "klangstab", sound = AlertSound.KLANGSTAB),
    TIEFE_SCHALE(key = "tiefe_schale", sound = AlertSound.TIEFE_SCHALE),

    /** „Ting (leise)": the tick of M1 (D-045), stored as `ting`. */
    TING(key = "ting", sound = AlertSound.TICK),
    ;

    companion object {
        /** The default of SET-26 ([UserSettings.DEFAULT_PRE_ALERT_SOUND]). */
        val DEFAULT: PreAlertSound get() = entries.first { it.key == UserSettings.DEFAULT_PRE_ALERT_SOUND }

        /** The choice stored under [key]; an unknown key reads as the default (05 §6). */
        fun of(key: String): PreAlertSound = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
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
 * SET-14 Alarmdauer (04 §9.1): Kurz = once (null), Normal = repeat up to
 * 15 s, Lang = repeat up to 2 min (until acknowledged; acknowledging calls
 * `stopAlert()`).
 */
val AlertLength.limitMs: Long?
    get() = when (this) {
        AlertLength.SHORT -> null
        AlertLength.NORMAL -> 15_000L
        AlertLength.LONG -> 120_000L
    }

/** How many tone cycles of [cycleMs] fit into the limit of this length; at least one. */
fun AlertLength.cycles(cycleMs: Long): Int {
    val limit = limitMs ?: return 1
    return (limit / cycleMs).toInt().coerceAtLeast(1)
}

/**
 * The user's alert settings: tone (SET-09), alert length (SET-14), vibration (SET-11) and the
 * pre-alert's sound (SET-26).
 */
data class AlertSettings(
    val tone: AlertSound = AlertSound.KLANGSCHALE,
    val length: AlertLength = AlertLength.NORMAL,
    val vibration: Boolean = true,
    val preAlert: AlertSound = PreAlertSound.DEFAULT.sound,
) {
    /** The sounds a timer's next tick and alert play, which stay loaded while it is active (D-092). */
    val sounds: Set<AlertSound> get() = linkedSetOf(preAlert, tone)

    companion object {
        /**
         * The alert part of the stored [settings] (05 §6); an unknown tone key is the Klangschale,
         * an unknown pre-alert key the default *Vorwarnton*.
         */
        fun of(settings: UserSettings): AlertSettings = AlertSettings(
            tone = AlertSound.tone(settings.alertSound),
            length = settings.alertLength,
            vibration = settings.vibration,
            preAlert = PreAlertSound.of(settings.preAlertSound).sound,
        )
    }
}
