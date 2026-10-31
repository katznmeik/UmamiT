package com.umamit.core.timer.alert

import com.umamit.core.model.AlertLength
import com.umamit.core.model.UserSettings

/** The phone's ringer mode (04 §9.1), read when an alert or pre-alert starts and on every repeat. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/**
 * Do Not Disturb as the interruption filter reports it (04 §9.1, D-098), read with the ringer mode;
 * in priority mode also whether its policy lets alarms through (D-099). The cues play on the alarm
 * stream (`USAGE_ALARM`), which *alarms only* and priority mode with alarms allowed let through;
 * *total silence* mutes it.
 */
enum class DoNotDisturb {
    /** Off (`INTERRUPTION_FILTER_ALL`, or a filter the platform cannot say). */
    OFF,

    /**
     * Priority mode (`INTERRUPTION_FILTER_PRIORITY`) whose policy lets alarms through: One UI's Do Not
     * Disturb with its default exceptions. Also priority mode whose policy cannot be read (rule 8, D-099).
     */
    PRIORITY,

    /** Priority mode whose policy blocks alarms (no `PRIORITY_CATEGORY_ALARMS`, D-099). */
    PRIORITY_WITHOUT_ALARMS,

    /** „Nur Alarme" (`INTERRUPTION_FILTER_ALARMS`). */
    ALARMS_ONLY,

    /** „Absolute Ruhe" (`INTERRUPTION_FILTER_NONE`). */
    TOTAL_SILENCE,
}

/**
 * What the device allows right now.
 *
 * @property inCall a call is ringing or active (incl. VoIP): vibrate only (04 §9.1).
 * @property dnd Do Not Disturb (04 §9.1, D-098, D-099): *total silence* keeps the cue visual; *alarms
 *   only* and priority mode play as the ringer mode, where a silent report may ring ([AlertMatrix]).
 */
data class AlertConditions(
    val ringerMode: RingerMode,
    val inCall: Boolean = false,
    val dnd: DoNotDisturb = DoNotDisturb.OFF,
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
 * haptic) and the alert (tone + vibration pattern). The alarm stream ignores the
 * ringer mode, so the app applies it itself on every cycle (D-098):
 *
 * | Ringer mode | Outputs |
 * |---|---|
 * | Normal | sound + vibration (vibration only if SET-11 is on) |
 * | Normal, in a call | vibration only |
 * | Vibrate | vibration |
 * | Silent | none (visual only) |
 *
 * Do Not Disturb (D-098, D-099): off and priority mode as the ringer mode,
 * *alarms only* too; *total silence* none. A DND that lets alarms through can
 * report the ringer as silent whatever the user chose, so there a silent report
 * reads as normal (sound, plus vibration if SET-11 is on; a call still means
 * vibration only):
 * - *alarms only*: always. AOSP's zen mode forces the internal and the reported
 *   ringer mode to silent there (D-098 decision 8, rule 8).
 * - priority mode with alarms allowed: while [ringUnderDnd] („Bei „Nicht
 *   stören" klingeln", SET-32, default on). One UI reports silent under its DND
 *   with the phone on ring (the PO's v0.3.0-rc.1 test, D-099); off, the
 *   reported ringer mode decides, as before.
 *
 * Priority mode whose policy blocks alarms plays as reported; a vibrate report
 * stays vibrate everywhere.
 */
object AlertMatrix {
    fun outputs(
        conditions: AlertConditions,
        vibrationEnabled: Boolean = true,
        ringUnderDnd: Boolean = true,
    ): AlertOutputs {
        if (conditions.dnd == DoNotDisturb.TOTAL_SILENCE) return AlertOutputs.NONE
        val silentByDnd = conditions.ringerMode == RingerMode.SILENT && when (conditions.dnd) {
            DoNotDisturb.ALARMS_ONLY -> true
            DoNotDisturb.PRIORITY -> ringUnderDnd
            DoNotDisturb.OFF, DoNotDisturb.PRIORITY_WITHOUT_ALARMS, DoNotDisturb.TOTAL_SILENCE -> false
        }
        val ringerMode = if (silentByDnd) RingerMode.NORMAL else conditions.ringerMode
        return when (ringerMode) {
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
 * The user's alert settings: tone (SET-09), alert length (SET-14), vibration (SET-11), the
 * pre-alert's sound (SET-26), *Lautstärke* (SET-27, D-098) and *Bei „Nicht stören" klingeln*
 * ([ringUnderDnd], SET-32, D-099: [AlertMatrix]).
 */
data class AlertSettings(
    val tone: AlertSound = AlertSound.KLANGSCHALE,
    val length: AlertLength = AlertLength.NORMAL,
    val vibration: Boolean = true,
    val preAlert: AlertSound = PreAlertSound.DEFAULT.sound,
    val volumePercent: Int = UserSettings.MAX_ALERT_VOLUME_PERCENT,
    val ringUnderDnd: Boolean = true,
) {
    /** The sounds a timer's next tick and alert play, which stay loaded while it is active (D-092). */
    val sounds: Set<AlertSound> get() = linkedSetOf(preAlert, tone)

    /**
     * The player's volume on top of the alarm stream (SET-27): *Lautstärke* / 100, never below 0.1, so the
     * app itself never silences a cue (rule 8).
     */
    val gain: Float get() = clampVolume(volumePercent) / PERCENT

    companion object {
        private const val PERCENT = 100f

        /**
         * The alert part of the stored [settings] (05 §6); an unknown tone key is the Klangschale,
         * an unknown pre-alert key the default *Vorwarnton*, and a *Lautstärke* outside 10–100 is clamped.
         */
        fun of(settings: UserSettings): AlertSettings = AlertSettings(
            tone = AlertSound.tone(settings.alertSound),
            length = settings.alertLength,
            vibration = settings.vibration,
            preAlert = PreAlertSound.of(settings.preAlertSound).sound,
            volumePercent = clampVolume(settings.alertVolumePercent),
            ringUnderDnd = settings.ringUnderDnd,
        )

        private fun clampVolume(percent: Int): Int =
            percent.coerceIn(UserSettings.MIN_ALERT_VOLUME_PERCENT, UserSettings.MAX_ALERT_VOLUME_PERCENT)
    }
}
