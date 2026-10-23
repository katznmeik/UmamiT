package com.umamit.core.timer.diagnostics

import com.umamit.core.timer.controller.TimerAlarmKind

/**
 * When a time-based event was due and when the controller handled it, read back from its
 * diagnostics log line (D-060, TMR-30): the inverse of the timing part of
 * [TimerDiagnosticFormat] for an accepted event, e.g.
 *
 * `event DeadlineReached from WAKE_UP: RUNNING -> ALERTING accepted | DEADLINE scheduled
 * 10:01:00.000 (mono 5060000) actual 10:01:00.012 (mono 5060012) delta +12 ms`.
 *
 * The accuracy workflow (04 §12, #52) reads the pre-alert and the alert of every step this
 * way; the round-trip test keeps this parser and the log format together.
 */
data class FireTime(
    /** The alarm kind the event stands for (`PRE_ALERT`, `DEADLINE`, …). */
    val kind: TimerAlarmKind,
    /** What delivered it: `ALARM`, `WAKE_UP` or `RECOVERY` ([com.umamit.core.timer.controller.EventSource]). */
    val source: String,
    /** The scheduled local time of day, `HH:mm:ss.SSS`. */
    val scheduledWall: String,
    val scheduledMonoMs: Long,
    /** The actual local time of day, `HH:mm:ss.SSS`. */
    val actualWall: String,
    val actualMonoMs: Long,
    /** How late the event was handled, in ms, on the kind's own clock (negative: early). */
    val deltaMs: Long,
) {
    companion object {
        private val ACCEPTED = Regex(
            """^event (\w+) from (\w+): \w+ -> \w+ accepted \| (\w+) """ +
                """scheduled (\S+) \(mono (-?\d+)\) actual (\S+) \(mono (-?\d+)\) delta ([+-]\d+) ms$""",
        )

        /**
         * The fire time in the log [message] of an accepted time-based event; null for any
         * other entry (an ignored or rejected event, an alarm that found its event handled).
         */
        fun parse(message: String): FireTime? {
            val match = ACCEPTED.matchEntire(message.trim()) ?: return null
            val (event, source, kindName, scheduledWall, scheduledMono, actualWall, actualMono, delta) =
                match.destructured
            val kind = TimerAlarmKind.entries.firstOrNull { it.name == kindName } ?: return null
            if (kind.event::class.simpleName != event) return null
            return FireTime(
                kind = kind,
                source = source,
                scheduledWall = scheduledWall,
                scheduledMonoMs = scheduledMono.toLong(),
                actualWall = actualWall,
                actualMonoMs = actualMono.toLong(),
                deltaMs = delta.toLong(),
            )
        }
    }
}
