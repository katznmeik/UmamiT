package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind

/** One answer of „Welchen Timer beenden?" (TMR-21, D-108): the active timer of [slot], [teaName] (null: Schnelltimer). */
data class TimerToEnd(val slot: TimerSlot, val teaName: String?)

/**
 * What a screen does with a refused start (TMR-21, TMR-22, D-108), read from the [BrewStarter]'s refusal and its
 * active timers. Only the screens read it: the controller's admission is its own (04 §3.3).
 */
sealed interface StartRefusal {
    /** The tea has an active session already (a tea has at most one): the screens open it, in [slot]. */
    data class OpenSession(val slot: TimerSlot) : StartRefusal

    /** A second quick timer: „Schnelltimer beenden und neu starten?" (D-098). */
    data object RestartQuick : StartRefusal

    /** Two timers are active: „Welchen Timer beenden?" with one answer per active timer, in the slot order. */
    data class ChooseTimer(val timers: List<TimerToEnd>) : StartRefusal
}

/**
 * The screens' answer to a start refused for [reason]; null for a refusal that asks nothing (an invalid quick timer)
 * or whose timer has ended meanwhile.
 */
fun BrewStarter.refusalOf(reason: RejectionReason): StartRefusal? = when (reason) {
    is RejectionReason.ActiveTimerExists -> when (val kind = reason.active) {
        SessionKind.Quick -> StartRefusal.RestartQuick

        is SessionKind.Brew ->
            TimerSlot.entries
                .firstOrNull { activeIn(it)?.sessionId == kind.sessionId }
                ?.let(StartRefusal::OpenSession)
    }

    is RejectionReason.TwoTimersActive ->
        reason.active
            .mapNotNull { slot -> activeIn(slot)?.let { TimerToEnd(slot, it.teaName) } }
            .takeIf { it.isNotEmpty() }
            ?.let(StartRefusal::ChooseTimer)

    else -> null
}

/** The ids of the active brew sessions, in the slot order: [startedSlot] finds the new one after a start. */
fun BrewStarter.activeSessionIds(): List<String> = TimerSlot.entries.mapNotNull { activeIn(it)?.sessionId }

/**
 * The brew slot of the session that started after [before] ([activeSessionIds] read before the start), for the screen
 * to open (D-108); null when none did.
 */
fun BrewStarter.startedSlot(before: List<String>): TimerSlot? =
    TimerSlot.entries.firstOrNull { slot -> activeIn(slot)?.sessionId?.let { it !in before } == true }
