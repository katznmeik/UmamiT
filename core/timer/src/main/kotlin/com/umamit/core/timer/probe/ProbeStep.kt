package com.umamit.core.timer.probe

import android.content.Intent

/** Phase of the probe step; it only moves forward. */
enum class ProbePhase { RUNNING, PRE_ALERTED, FINISHED }

/**
 * One probe test step (M1-03a). Countdown arithmetic uses the monotonic
 * `elapsedRealtime()` clock; the wall clock is only used for the notification
 * chronometer (04 §5).
 *
 * @property startElapsedMs `SystemClock.elapsedRealtime()` at the start tap.
 * @property startWallMs `System.currentTimeMillis()` at the start tap.
 * @property durationMs planned step length.
 * @property preAlertLeadMs pre-alert lead N (04 §5); 0 = no pre-alert.
 */
data class ProbeStep(val startElapsedMs: Long, val startWallMs: Long, val durationMs: Long, val preAlertLeadMs: Long) {
    init {
        require(durationMs > 0) { "durationMs must be positive: $durationMs" }
        require(preAlertLeadMs >= 0) { "preAlertLeadMs must not be negative: $preAlertLeadMs" }
    }

    val deadlineElapsedMs: Long get() = startElapsedMs + durationMs

    /** Wall-clock deadline for `setWhen` of the chronometer countdown (04 §8.3). */
    val deadlineWallMs: Long get() = startWallMs + durationMs

    /**
     * Offset of the pre-alert from the start, or null when there is none:
     * N = 0, or the step is shorter than N + 5 s (04 §5).
     */
    val preAlertOffsetMs: Long?
        get() = if (preAlertLeadMs == 0L || durationMs < preAlertLeadMs + MIN_GAP_MS) {
            null
        } else {
            durationMs - preAlertLeadMs
        }

    val preAlertElapsedMs: Long? get() = preAlertOffsetMs?.let { startElapsedMs + it }

    fun phaseAt(nowElapsedMs: Long): ProbePhase {
        val preAlertAt = preAlertElapsedMs
        return when {
            nowElapsedMs >= deadlineElapsedMs -> ProbePhase.FINISHED
            preAlertAt != null && nowElapsedMs >= preAlertAt -> ProbePhase.PRE_ALERTED
            else -> ProbePhase.RUNNING
        }
    }

    /** Elapsed step time, clamped to `0..durationMs`. */
    fun elapsedAt(nowElapsedMs: Long): Long = (nowElapsedMs - startElapsedMs).coerceIn(0, durationMs)

    /** Remaining step time, clamped to `0..durationMs`. */
    fun remainingAt(nowElapsedMs: Long): Long = durationMs - elapsedAt(nowElapsedMs)

    /** Writes the step into [intent], so alarms and service starts carry it across process death. */
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_START_ELAPSED, startElapsedMs)
        .putExtra(EXTRA_START_WALL, startWallMs)
        .putExtra(EXTRA_DURATION, durationMs)
        .putExtra(EXTRA_PRE_ALERT_LEAD, preAlertLeadMs)

    companion object {
        /** 04 §5: no pre-alert when the step is shorter than N + 5 s. */
        const val MIN_GAP_MS = 5_000L

        private const val EXTRA_START_ELAPSED = "com.umamit.core.timer.extra.START_ELAPSED"
        private const val EXTRA_START_WALL = "com.umamit.core.timer.extra.START_WALL"
        private const val EXTRA_DURATION = "com.umamit.core.timer.extra.DURATION"
        private const val EXTRA_PRE_ALERT_LEAD = "com.umamit.core.timer.extra.PRE_ALERT_LEAD"

        /** Reads a step written by [writeTo]; null when the intent carries none or an invalid one. */
        fun readFrom(intent: Intent?): ProbeStep? {
            val duration = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
            val lead = intent?.getLongExtra(EXTRA_PRE_ALERT_LEAD, 0L) ?: 0L
            return if (intent == null || duration <= 0L || lead < 0L) {
                null
            } else {
                ProbeStep(
                    startElapsedMs = intent.getLongExtra(EXTRA_START_ELAPSED, 0L),
                    startWallMs = intent.getLongExtra(EXTRA_START_WALL, 0L),
                    durationMs = duration,
                    preAlertLeadMs = lead,
                )
            }
        }
    }
}
