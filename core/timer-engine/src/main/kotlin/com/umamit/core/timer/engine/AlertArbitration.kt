package com.umamit.core.timer.engine

import com.umamit.core.model.TimerSlot

/**
 * The alert arbitration of 04 §9.3 (D-098; PO Q2, R2-Q2, S1) between the controller's two slots, as pure rules
 * the controller applies in every turn. The engine stays a single-timer state machine; these rules decide which
 * events the quick slot gets so that only one slot rings, and the brew's alert first:
 *
 * - **Priority:** the brew is never held. A quick deadline (or a quick timer's recovery) while the brew slot is
 *   ALERTING becomes [TimerEvent.AlertHold] ([TimerEvent.RecoverHeld]) instead ([quickEvent]).
 * - **Preemption:** a quick alert that still rings when the brew starts alerting is held again ([settle]).
 * - **Release:** a held quick alert rings as soon as the brew slot is no longer ALERTING ([settle]).
 *
 * Which sound the one player may play at a moment is [PlayerSound]'s part.
 */
object AlertArbitration {

    /**
     * The event the quick slot gets for [event] while the brew slot is in [brew]: while the brew alerts, its
     * deadline holds the quick alert ([TimerEvent.AlertHold]) and its recovery recovers held
     * ([TimerEvent.RecoverHeld]); every other event as it is.
     */
    fun quickEvent(event: TimerEvent, brew: TimerState): TimerEvent = when {
        brew !is TimerState.Alerting -> event
        event == TimerEvent.DeadlineReached -> TimerEvent.AlertHold
        event == TimerEvent.Recover -> TimerEvent.RecoverHeld
        else -> event
    }

    /**
     * After an event of either slot: the event the quick slot in [quick] needs so that at most one slot rings,
     * the brew in [brew] first. [TimerEvent.AlertHold] while the brew alerts and the quick alert rings
     * (preemption), [TimerEvent.AlertRingStart] when the brew no longer alerts and the quick alert is held
     * (release); null when the two already agree.
     */
    fun settle(brew: TimerState, quick: TimerState): TimerEvent? {
        val alert = quick as? TimerState.Alerting ?: return null
        val brewAlerts = brew is TimerState.Alerting
        return when {
            brewAlerts && !alert.held -> TimerEvent.AlertHold
            !brewAlerts && alert.held -> TimerEvent.AlertRingStart
            else -> null
        }
    }
}

/**
 * The one alert player's sound as the controller's arbitration sees it (04 §5, §9.3; D-098): never two sounds
 * at once, and a cue of one slot never cuts the other slot's alert.
 *
 * - An alert ([alertStarted]) takes the player, cutting any pre-alert that still rings, as within one slot. It
 *   ends with the player's report or the slot's `StopAlert` ([alertEnded]).
 * - A `StopAlert` of one slot reaches the player only when the other slot does not own the sound ([mayStop]):
 *   the preempted quick alert's stop must not silence the brew's alert, and *Fertig* on a held alert must not
 *   cut the brew's pre-alert.
 * - A pre-alert due while the other slot's alert or pre-alert sounds waits ([preAlertDue]) until that sound
 *   ends, then plays only if at least 1 s of its step is left, else it is consumed silently ([release]). A
 *   pre-alert rings for [preAlertRingMs] (the longest pre-alert sound and the player's release margin), which
 *   the controller does not hear end.
 *
 * Pure and in-memory: a new process starts silent, with nothing waiting (a waiting pre-alert counts as played,
 * at most once, D-047).
 */
class PlayerSound(private val preAlertRingMs: Long) {

    /** What the player sounds for a slot: its alert, or its pre-alert until [untilElapsedMs]. */
    sealed interface Sound {
        val slot: TimerSlot

        data class Alert(override val slot: TimerSlot) : Sound

        data class PreAlert(override val slot: TimerSlot, val untilElapsedMs: Long) : Sound
    }

    /** A pre-alert of [slot] waiting for the other slot's sound, for the step that ends at [deadlineElapsedMs]. */
    data class Waiting(val slot: TimerSlot, val deadlineElapsedMs: Long)

    private var sound: Sound? = null
    private val waiting = mutableMapOf<TimerSlot, Waiting>()

    /** The sound at [nowElapsedMs]; a pre-alert counts until it has rung out. */
    fun soundAt(nowElapsedMs: Long): Sound? = sound?.takeUnless {
        it is Sound.PreAlert &&
            it.untilElapsedMs <= nowElapsedMs
    }

    /** The pre-alerts waiting for a sound to end. */
    val waitingPreAlerts: Collection<Waiting> get() = waiting.values

    /** The alert of [slot] starts: it takes the player. A pre-alert of [slot] still waiting will not come. */
    fun alertStarted(slot: TimerSlot) {
        sound = Sound.Alert(slot)
        waiting.remove(slot)
    }

    /** The alert of [slot] ended (the player's report, or its stop); another slot's sound stays. */
    fun alertEnded(slot: TimerSlot) {
        if (sound == Sound.Alert(slot)) sound = null
    }

    /**
     * Whether a `StopAlert` of [slot] at [nowElapsedMs] may stop the player: not while the other slot's alert or
     * pre-alert sounds. The stop of [slot]'s own alert also ends it here.
     */
    fun mayStop(slot: TimerSlot, nowElapsedMs: Long): Boolean {
        val current = soundAt(nowElapsedMs)
        if (current != null && current.slot != slot) return false
        alertEnded(slot)
        return true
    }

    /**
     * A pre-alert of [slot] is due at [nowElapsedMs] for its step ending at [deadlineElapsedMs]: true when it
     * plays now (it then owns the sound), false when it waits for the other slot's sound.
     */
    fun preAlertDue(slot: TimerSlot, deadlineElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (blocked(slot, nowElapsedMs)) {
            waiting[slot] = Waiting(slot, deadlineElapsedMs)
            return false
        }
        sound = Sound.PreAlert(slot, nowElapsedMs + preAlertRingMs)
        return true
    }

    /**
     * The waiting pre-alerts whose blocking sound has ended at [nowElapsedMs]: those that play now, at least 1 s
     * before the deadline of a step still counting down to it ([runningDeadlineOf]: the deadline of the slot's
     * running step, null when none runs), each then owning the sound; the others are consumed silently.
     */
    fun release(nowElapsedMs: Long, runningDeadlineOf: (TimerSlot) -> Long?): List<Waiting> {
        val played = mutableListOf<Waiting>()
        TimerSlot.entries.mapNotNull { waiting[it] }.forEach { pending ->
            // The step it belongs to still counts down to the same deadline (not paused, ended or replaced).
            val deadline = runningDeadlineOf(pending.slot)?.takeIf { it == pending.deadlineElapsedMs }
            when {
                deadline == null -> waiting.remove(pending.slot)

                blocked(pending.slot, nowElapsedMs) -> Unit

                else -> {
                    waiting.remove(pending.slot)
                    if (deadline - nowElapsedMs >= PRE_ALERT_MIN_LEFT_MS) {
                        sound = Sound.PreAlert(pending.slot, nowElapsedMs + preAlertRingMs)
                        played += pending
                    }
                }
            }
        }
        return played
    }

    /** When a waiting pre-alert may play next because the pre-alert it waits for has rung out; null otherwise. */
    fun nextReleaseAtElapsedMs(): Long? =
        (sound as? Sound.PreAlert)?.untilElapsedMs?.takeIf { waiting.keys.any { it != sound?.slot } }

    private fun blocked(slot: TimerSlot, nowElapsedMs: Long): Boolean =
        soundAt(nowElapsedMs)?.let { it.slot != slot } ?: false
}
