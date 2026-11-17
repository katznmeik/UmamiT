package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.engine.RejectionReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The open question of one screen's refused start (TMR-21, TMR-22, D-108), for the setup, the quick timer and Start:
 * it holds the question [Q] that [ask] makes of a [StartRefusal] (null: that refusal asks nothing) with the start
 * [R] it answers, and closes it when an alert starts. „No question over an alert" (rule 8) lives here: a refusal
 * during an alert opens the alerting timer instead, and an alert that starts closes the question.
 *
 * It has no scope of its own: the screen's view model sends it [alertChanged] from its timer state, reads [refused]
 * for a refused start and opens what that returns.
 */
class StartQuestions<Q : Any, R>(private val starter: BrewStarter, private val ask: (StartRefusal) -> Q?) {
    private val pending = MutableStateFlow<Asked<Q, R>?>(null)

    /** The question shown now, or null. */
    val question: Flow<Q?> = pending.map { it?.question }

    /**
     * The alert playing now changed to [alertKey] (null: none). A question is only asked while no alert plays, so
     * any alert covers it: it closes for good (rule 8).
     */
    fun alertChanged(alertKey: String?) {
        if (alertKey != null) pending.value = null
    }

    /**
     * A start of [request] was refused for [reason]: what the screen opens, or [StartReply.Stay] when it asks
     * (the question is then [question]) or there is nothing to do.
     */
    fun refused(reason: RejectionReason, request: R): StartReply {
        val refusal = starter.refusalOf(reason)
        val question = refusal?.let(ask)
        return when {
            // The tea's own session (a tea has at most one, D-108): it opens, showing a ringing alert first.
            refusal is StartRefusal.OpenSession -> StartReply.OpenSession(refusal.slot)

            refusal == null || question == null -> StartReply.Stay

            // No question over an alert (rule 8): the alerting timer is where to go now.
            starter.state.value.alertKey() != null -> StartReply.OpenAlerting

            else -> {
                pending.value = Asked(question, request, (refusal as? StartRefusal.ChooseTimer)?.timers.orEmpty())
                StartReply.Stay
            }
        }
    }

    /**
     * An answer of the question: the timer of [slot] ends. The question closes; returns the start it answered and
     * the session the answer named (one that started in its slot meanwhile is not ended, #194), or null when no
     * question is open.
     */
    fun answer(slot: TimerSlot): StartAnswer<R>? {
        val asked = pending.value ?: return null
        pending.value = null
        return StartAnswer(asked.request, asked.timers.firstOrNull { it.slot == slot }?.sessionId)
    }

    /** *Abbrechen*: the question closes and every timer stays. */
    fun dismiss() {
        pending.value = null
    }

    private data class Asked<Q, R>(val question: Q, val request: R, val timers: List<TimerToEnd>)
}

/** What a screen opens after [StartQuestions.refused]. */
sealed interface StartReply {
    /** The tea's own session, in [slot]. */
    data class OpenSession(val slot: TimerSlot) : StartReply

    /** The timer in front, which alerts: no question over an alert (rule 8). */
    data object OpenAlerting : StartReply

    /** Nothing to open: the question is asked, or the refusal asks nothing. */
    data object Stay : StartReply
}

/** An answer of [StartQuestions.answer]: the start [request] again, after the timer of [endingSession] ends. */
data class StartAnswer<R>(val request: R, val endingSession: String?)
