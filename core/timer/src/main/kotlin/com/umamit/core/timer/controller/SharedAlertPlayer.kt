package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alert.PreAlertSound
import com.umamit.core.timer.alert.RingerModeAlertPlayer
import com.umamit.core.timer.engine.PlayerSound
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerState

/**
 * The one alert player both slots share (04 §3.3, §9.3; D-098), driven through the [alert] port with the
 * arbitration of [PlayerSound]: never two sounds at once, a cue of one slot never cuts the other slot's alert,
 * and a pre-alert due while the other slot's sound rings waits for it and plays only with at least 1 s left
 * (04 §5). The player's own code is unchanged: this decides only which commands it gets. Used inside the
 * controller's event lock; a port that throws is reported through [failed].
 */
internal class SharedAlertPlayer(
    private val alert: TimerAlertPort,
    private val failed: (ClockReading, TimerEffect, Exception) -> Unit,
) {
    /** The alert the player plays, between `StartAlert` and its end or a `StopAlert` that reached it. */
    private var playingAlert: StepAlert? = null

    private val sound = PlayerSound(PRE_ALERT_RING_MS)

    /** When each waiting pre-alert was due, for the player's log once it plays (D-068). */
    private val waitingScheduled = mutableMapOf<TimerSlot, ScheduledTime?>()

    /** The slot whose alert plays (its watchdog follows it); null while none plays. */
    val alertPlayingIn: TimerSlot? get() = playingAlert?.let { slotOf(it.kind) }

    /** True while the alert of the alerting [state] plays. */
    fun isPlaying(state: TimerState.Alerting): Boolean = playingAlert == stepAlertOf(state)

    /**
     * `StartAlert` of [slot] (04 §4, D-048): ensures the alert of the alerting [state] plays; an alert already
     * playing for the same deadline is not restarted. It takes the player from any other sound.
     */
    fun startAlert(slot: TimerSlot, state: TimerState.Alerting) {
        val stepAlert = stepAlertOf(state)
        if (stepAlert == playingAlert) return
        sound.alertStarted(slot)
        waitingScheduled.remove(slot)
        alert.startAlert(stepAlert, ScheduledTime(TimerAlarmKind.DEADLINE, state.deadlineElapsedMs))
        playingAlert = stepAlert
    }

    /**
     * `StopAlert` of [slot]: stops the player unless the other slot owns its sound, so a preempted quick
     * alert's stop never silences the brew's alert and *Fertig* on a held alert never cuts a pre-alert.
     */
    fun stopAlert(slot: TimerSlot, now: ClockReading) {
        if (!sound.mayStop(slot, now.elapsedMs)) return
        if (alertPlayingIn == slot) playingAlert = null
        alert.stopAlert()
    }

    /**
     * `PlayPreAlert` of [slot]'s step that ends at [deadlineElapsedMs], due at [scheduled]: now, unless the
     * other slot's alert or pre-alert sounds; then it waits for that sound to end ([releasePreAlerts]).
     */
    fun preAlert(slot: TimerSlot, deadlineElapsedMs: Long, scheduled: ScheduledTime?, now: ClockReading) {
        if (sound.preAlertDue(slot, deadlineElapsedMs, now.elapsedMs)) {
            alert.playPreAlert(scheduled)
        } else {
            waitingScheduled[slot] = scheduled
        }
    }

    /**
     * The player reported the end of [ended]: true when it is the playing alert, which then no longer plays;
     * false for any other (stopped, or an earlier step's), which must change nothing.
     */
    fun alertEnded(ended: StepAlert): Boolean {
        if (ended != playingAlert) return false
        playingAlert = null
        sound.alertEnded(slotOf(ended.kind))
        return true
    }

    /**
     * At the end of every turn: the pre-alerts that waited for the other slot's sound and whose sound has ended
     * play now if at least 1 s of their step is left ([runningDeadlineOf]: the deadline of a slot's running
     * step); the others are consumed silently (04 §5, §9.3).
     */
    fun releasePreAlerts(now: ClockReading, runningDeadlineOf: (TimerSlot) -> Long?) {
        if (sound.waitingPreAlerts.isEmpty()) return
        sound.release(now.elapsedMs, runningDeadlineOf).forEach { waiting ->
            attempt { alert.playPreAlert(waitingScheduled[waiting.slot]) }
                ?.let { failed(now, TimerEffect.PlayPreAlert, it) }
        }
        val stillWaiting = sound.waitingPreAlerts.map { it.slot }.toSet()
        waitingScheduled.keys.retainAll(stillWaiting)
    }

    /** When a waiting pre-alert may play because the pre-alert it waits for has rung out (a wake-up comes then). */
    val nextPreAlertReleaseAtElapsedMs: Long? get() = sound.nextReleaseAtElapsedMs()
}

/** The alert of the alerting step of [state]: one per deadline (D-048). */
private fun stepAlertOf(state: TimerState.Alerting) =
    StepAlert(state.session.kind, state.stepIndex, state.deadlineWallMs)

/**
 * How long the controller counts a pre-alert as ringing (04 §9.2, §9.3): the longest pre-alert sound and the
 * player's release of its focus after it, as the controller does not hear it end. A pre-alert of the other
 * slot waits this long at most behind it.
 */
private val PRE_ALERT_RING_MS: Long =
    PreAlertSound.entries.maxOf { it.sound.lengthMs } + RingerModeAlertPlayer.RELEASE_MARGIN_MS
