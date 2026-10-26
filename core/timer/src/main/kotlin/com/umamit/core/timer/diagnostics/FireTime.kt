package com.umamit.core.timer.diagnostics

import com.umamit.core.timer.alert.AlertCue
import com.umamit.core.timer.controller.TimerAlarmKind

/**
 * When a step's pre-alert or alert was due and when something happened to it, read back from
 * its diagnostics log line (D-060, TMR-30): the inverse of the timing part of
 * [TimerDiagnosticFormat]. Two lines carry one:
 *
 * - [parse]: the controller handled the accepted time-based event, e.g.
 *   `event DeadlineReached from WAKE_UP: RUNNING -> ALERTING accepted | DEADLINE scheduled
 *   10:01:00.000 (mono 5060000) actual 10:01:00.012 (mono 5060012) delta +12 ms`;
 * - [parsePlayed]: the alert player started its sound (D-068), e.g.
 *   `alert ALERT: ringer NORMAL, … | tone klangschale, length NORMAL | queued 1 ms, settings 0 ms,
 *   outputs 12 ms, sound ready | DEADLINE scheduled 10:01:00.000 (mono 5060000) actual
 *   10:01:00.140 (mono 5060140) delta +140 ms` (the part on the start since D-092).
 *
 * The accuracy workflow (04 §12, #52) reads the pre-alert and the alert of every step this
 * way; the round-trip tests keep these parsers and the log format together.
 */
data class FireTime(
    /** The alarm kind the event stands for (`PRE_ALERT`, `DEADLINE`, …). */
    val kind: TimerAlarmKind,
    /**
     * What delivered it: `ALARM`, `WAKE_UP` or `RECOVERY` ([com.umamit.core.timer.controller.EventSource]);
     * [PLAYER] for the start of the sound.
     */
    val source: String,
    /** The scheduled local time of day, `HH:mm:ss.SSS`. */
    val scheduledWall: String,
    val scheduledMonoMs: Long,
    /** The actual local time of day, `HH:mm:ss.SSS`. */
    val actualWall: String,
    val actualMonoMs: Long,
    /** How late it happened, in ms, on the kind's own clock (negative: early). */
    val deltaMs: Long,
    /**
     * For a sound start: where the player's start spent the time, as logged (D-092), e.g.
     * `queued 1 ms, settings 0 ms, outputs 12 ms, sound ready`; null for a handled event or a
     * line without it.
     */
    val start: String? = null,
) {
    companion object {
        /** The [source] of a fire time read by [parsePlayed]: the alert player started the sound. */
        const val PLAYER = "PLAYER"

        private const val TIMING =
            """(\w+) scheduled (\S+) \(mono (-?\d+)\) actual (\S+) \(mono (-?\d+)\) delta ([+-]\d+) ms"""

        private val ACCEPTED = Regex("""^event (\w+) from (\w+): \w+ -> \w+ accepted \| $TIMING$""")

        // An empty second group, so both regexes share the group numbers below.
        private val PLAYED = Regex("""^alert (\w+)(): .* \| $TIMING$""")

        /** The start part of a sound start's line (D-092). */
        private val START = Regex("""\| (queued -?\d+ ms, settings -?\d+ ms, outputs -?\d+ ms, [^|]+?) \|""")

        /**
         * The fire time in the log [message] of an accepted time-based event; null for any
         * other entry (an ignored or rejected event, an alarm that found its event handled).
         */
        fun parse(message: String): FireTime? {
            val values = ACCEPTED.matchEntire(message.trim())?.groupValues ?: return null
            val kind = TimerAlarmKind.entries.firstOrNull { it.name == values[KIND] }
            return kind?.takeIf { it.event != null && it.event::class.simpleName == values[EVENT] }
                ?.let { fireTime(it, values[SOURCE], values) }
        }

        /**
         * The moment the sound of a step's pre-alert or alert started, in the log [message] of
         * the alert player (D-068), with the time it was due; null for any other entry, a test
         * (*Alarm testen*) and a cue logged without its due time.
         */
        fun parsePlayed(message: String): FireTime? {
            val values = PLAYED.matchEntire(message.trim())?.groupValues ?: return null
            val kind = when (values[EVENT]) {
                AlertCue.PRE_ALERT.name -> TimerAlarmKind.PRE_ALERT
                AlertCue.ALERT.name -> TimerAlarmKind.DEADLINE
                else -> null
            }
            return kind?.takeIf { it.name == values[KIND] }
                ?.let { fireTime(it, PLAYER, values).copy(start = START.find(message)?.groupValues?.get(1)) }
        }

        private fun fireTime(kind: TimerAlarmKind, source: String, values: List<String>) = FireTime(
            kind = kind,
            source = source,
            scheduledWall = values[SCHEDULED_WALL],
            scheduledMonoMs = values[SCHEDULED_MONO].toLong(),
            actualWall = values[ACTUAL_WALL],
            actualMonoMs = values[ACTUAL_MONO].toLong(),
            deltaMs = values[DELTA].toLong(),
        )

        // The groups of ACCEPTED and PLAYED; PLAYED has the cue as EVENT and an empty SOURCE.
        private const val EVENT = 1
        private const val SOURCE = 2
        private const val KIND = 3
        private const val SCHEDULED_WALL = 4
        private const val SCHEDULED_MONO = 5
        private const val ACTUAL_WALL = 6
        private const val ACTUAL_MONO = 7
        private const val DELTA = 8
    }
}
