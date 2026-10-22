package com.umamit.feature.brew.timer

import androidx.compose.runtime.Immutable
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.StepStatus
import com.umamit.feature.brew.R

/** The user's screen settings for the timer (SET-17, SET-25). */
@Immutable
data class TimerScreenSettings(val keepScreenOn: Boolean = true, val dimWhileSteeping: Boolean = true)

/** The overflow ⋮ items of the timer screen, in menu order (03 SCR-05). */
enum class TimerMenuItem {
    /** *Brühen anpassen*: back to the setup, only before the first step started. */
    AdjustBrew,

    /** *Aufguss jetzt beenden* (TMR-05). */
    EndStepNow,

    /** *Schritt überspringen* (TMR-09). */
    SkipStep,

    /** *Schritte bearbeiten* → Schritte-Sheet (TMR-11). */
    EditSteps,

    /** *Weiterer Aufguss* (TMR-10), then the Schritte-Sheet. */
    AddStep,

    /** *Sitzung beenden* (TMR-12). */
    FinishSession,

    /** *Sitzung verwerfen* (TMR-13). */
    DiscardSession,

    /** *Timer-Einstellungen*. */
    TimerSettings,
}

/**
 * The overflow items of this state, exactly as the SCR-05 table lists them.
 * In ALERTING nothing opens the Schritte-Sheet (D-051, D-055, rule 8).
 */
fun TimerUiState.Active.menuItems(): List<TimerMenuItem> = TimerMenuItem.entries.filter { item ->
    when (item) {
        TimerMenuItem.AdjustBrew -> phase == TimerPhase.READY && steps.all { it.status == StepStatus.PENDING }
        TimerMenuItem.EndStepNow -> phase == TimerPhase.RUNNING || phase == TimerPhase.PAUSED
        TimerMenuItem.SkipStep -> phase == TimerPhase.READY
        TimerMenuItem.EditSteps, TimerMenuItem.AddStep -> phase != TimerPhase.ALERTING
        TimerMenuItem.FinishSession, TimerMenuItem.DiscardSession, TimerMenuItem.TimerSettings -> true
    }
}

/** What TalkBack announces on the timer screen (03 SCR-05, §6). */
enum class TimerAnnouncement {
    Ready,
    Running,

    /** The pre-alert („Gleich fertig …", TMR-23). */
    PreAlert,

    /** „Noch 10 Sekunden". */
    TenSeconds,

    /** A 10 s pre-alert: both at the same moment, announced once. */
    PreAlertTenSeconds,
    Paused,

    /** „Jetzt abgießen!" (TMR-06). */
    Alert,
    Finished,
}

private const val MS_PER_SECOND = 1_000L
private const val TEN_SECONDS = 10L

/** From this much overtime on, the overtime is shown large (TMR-06, D-055). */
const val PROMINENT_OVERTIME_SECONDS = 15L

/** A long step's ring and fill move only this often (03 §3.6 `longStepGlide`). */
const val LONG_STEP_UPDATE_MS = 10_000L

/**
 * The identity of the alert playing now (the step and its deadline), or null
 * when none plays. Each alert has its own: an overlay or a route opened during
 * one alert never stays over the next (rule 8, D-058).
 */
fun TimerUiState.alertKey(): String? = (this as? TimerUiState.Active)?.takeIf { it.alerting }?.let {
    "${it.sessionId ?: "quick"}:${it.stepIndex}:${it.deadlineWallMs}"
}

/** RUNNING: the pre-alert time has come, or its tick played; the caption reads „Gleich fertig …" (TMR-23). */
fun TimerUiState.Active.preAlertReached(nowElapsedMs: Long): Boolean = phase == TimerPhase.RUNNING &&
    (preAlertPlayed || preAlertAtElapsedMs?.let { nowElapsedMs >= it } == true)

/** What TalkBack announces at [nowElapsedMs]; a change of the value is announced. */
fun TimerUiState.Active.announcement(nowElapsedMs: Long): TimerAnnouncement = when (phase) {
    TimerPhase.READY -> TimerAnnouncement.Ready
    TimerPhase.PAUSED -> TimerAnnouncement.Paused
    TimerPhase.ALERTING -> TimerAnnouncement.Alert
    TimerPhase.FINISHED -> TimerAnnouncement.Finished
    TimerPhase.RUNNING -> runningAnnouncement(nowElapsedMs)
}

private fun TimerUiState.Active.runningAnnouncement(nowElapsedMs: Long): TimerAnnouncement {
    val preAlert = preAlertReached(nowElapsedMs)
    val tenSeconds = (currentStep?.plannedSeconds ?: 0) > TEN_SECONDS &&
        display(nowElapsedMs).remainingSeconds <= TEN_SECONDS
    val lead = preAlertLeadSeconds ?: 0
    return when {
        // Both reached: the later of the two is the news.
        preAlert && tenSeconds -> when {
            lead < TEN_SECONDS -> TimerAnnouncement.PreAlert
            lead == TEN_SECONDS.toInt() -> TimerAnnouncement.PreAlertTenSeconds
            else -> TimerAnnouncement.TenSeconds
        }

        preAlert -> TimerAnnouncement.PreAlert

        tenSeconds -> TimerAnnouncement.TenSeconds

        else -> TimerAnnouncement.Running
    }
}

/**
 * How long until what the screen shows changes next (a digit of the countdown
 * or the overtime, or the pre-alert caption); null while nothing moves by
 * itself (READY, PAUSED, FINISHED, or a deadline the controller has not
 * handled yet).
 */
fun TimerUiState.Active.nextTickDelayMs(nowElapsedMs: Long): Long? {
    val deadline = deadlineElapsedMs
    return when {
        deadline == null -> null
        phase == TimerPhase.RUNNING -> runningTickDelayMs(deadline - nowElapsedMs, nowElapsedMs)
        phase == TimerPhase.ALERTING -> MS_PER_SECOND - (nowElapsedMs - deadline).coerceAtLeast(0) % MS_PER_SECOND
        else -> null
    }
}

private fun TimerUiState.Active.runningTickDelayMs(remainingMs: Long, nowElapsedMs: Long): Long? {
    if (remainingMs <= 0) return null
    val toSecond = (remainingMs % MS_PER_SECOND).takeIf { it > 0 } ?: MS_PER_SECOND
    val toPreAlert = preAlertAtElapsedMs?.minus(nowElapsedMs)?.takeIf { it > 0 && !preAlertPlayed }
    return minOf(toSecond, toPreAlert ?: toSecond)
}

/**
 * The elapsed fraction of the current step, 0 (READY) to 1 (ALERTING,
 * FINISHED). A long step moves only at each [LONG_STEP_UPDATE_MS] of its run,
 * so the ring glides there instead of redrawing every frame (03 §3.6).
 */
fun TimerUiState.Active.progressAt(nowElapsedMs: Long): Float {
    val plannedMs = (currentStep?.plannedSeconds ?: 0) * MS_PER_SECOND
    if (plannedMs <= 0) return if (phase == TimerPhase.READY) 0f else 1f
    val elapsedMs = when (phase) {
        TimerPhase.READY -> 0L
        TimerPhase.RUNNING -> plannedMs - (checkNotNull(deadlineElapsedMs) - nowElapsedMs)
        TimerPhase.PAUSED -> plannedMs - checkNotNull(pausedRemainingMs)
        TimerPhase.ALERTING, TimerPhase.FINISHED -> plannedMs
    }
    val shown = if (longStep && phase == TimerPhase.RUNNING && elapsedMs < plannedMs) {
        elapsedMs - Math.floorMod(elapsedMs, LONG_STEP_UPDATE_MS)
    } else {
        elapsedMs
    }
    return (shown.toFloat() / plannedMs).coerceIn(0f, 1f)
}

internal fun TimerUiState.Active.caption(now: Long): Int = when (phase) {
    TimerPhase.READY -> R.string.brew_timer_caption_ready

    TimerPhase.RUNNING ->
        if (preAlertReached(now)) R.string.brew_timer_caption_pre_alert else R.string.brew_timer_caption_running

    TimerPhase.PAUSED -> R.string.brew_timer_caption_paused

    TimerPhase.ALERTING -> R.string.brew_timer_caption_alert

    TimerPhase.FINISHED -> R.string.brew_timer_caption_finished
}

/** The pre-alert tick on the ring; none for long steps (TMR-18) and outside a step. */
internal fun TimerUiState.Active.preAlertPosition(): Float? {
    val planned = currentStep?.plannedSeconds
    val lead = preAlertLeadSeconds
    // Not on the alert surface either: nothing but the alert there (D-058).
    val shown = planned != null && lead != null && !longStep && planned > lead &&
        phase != TimerPhase.FINISHED && phase != TimerPhase.ALERTING
    return if (shown) 1f - checkNotNull(lead).toFloat() / checkNotNull(planned) else null
}
