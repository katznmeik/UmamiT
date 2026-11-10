package com.umamit.core.timer.engine

import com.umamit.core.model.TimerSlot

/**
 * The alert arbitration of 04 §9.3 (D-098, D-108, D-109; PO Q2, R2-Q2, S1) over the controller's slots, as pure
 * rules the controller applies in every turn. The engine stays a single-timer state machine; these rules decide
 * which events a slot gets so that only one slot sounds at a time. An alert **sounds** while it alerts, is not held
 * and its sound has not ended (the player's `alertCompleted`, SET-14 *Alarmdauer*); one whose sound ended still
 * alerts („Jetzt abgießen!", its overtime counting) but holds nothing (D-109):
 *
 * - **Hold:** a deadline (or a recovery) of a slot while another slot's alert sounds becomes
 *   [TimerEvent.AlertHold] ([TimerEvent.RecoverHeld]) instead ([eventFor]), unless the slot preempts that alert:
 *   a brew preempts a sounding quick alert, a brew never preempts a brew, the quick timer never preempts.
 * - **Keeper:** of several sounding alerts one keeps the sound and every other is held again ([settle]): a brew's
 *   before the quick timer's, else the one that started ringing first, else the first in the slot order
 *   (BREW, BREW_2, QUICK).
 * - **Release:** when no alert sounds, the held alert whose deadline came first rings ([settle]); at the same
 *   deadline the first in the slot order, so a brew before the quick timer.
 *
 * With one brew in [TimerSlot.BREW] and the quick timer these are the rules of v0.3.0, except that a quick alert
 * waits only while the brew's sound plays, not until *Abgegossen* (D-109): the brew is never held. Which sound the
 * one player may play at a moment is [PlayerSound]'s part.
 */
object AlertArbitration {

    /** The event [event] of [slot] becomes, an arbitration event of [slot] after a turn ([settle]). */
    data class SlotEvent(val slot: TimerSlot, val event: TimerEvent)

    /**
     * The event [slot] gets for [event] while the slots are in [states] (a slot not in it is idle): while another
     * slot's alert sounds that [slot] does not preempt, its deadline holds its alert ([TimerEvent.AlertHold]) and
     * its recovery recovers held ([TimerEvent.RecoverHeld]); every other event as it is.
     */
    fun eventFor(slot: TimerSlot, event: TimerEvent, states: Map<TimerSlot, TimerState>): TimerEvent {
        val waits = states.any { (other, state) -> other != slot && state.sounds && !preempts(slot, other) }
        return when {
            !waits -> event
            event == TimerEvent.DeadlineReached -> TimerEvent.AlertHold
            event == TimerEvent.Recover -> TimerEvent.RecoverHeld
            else -> event
        }
    }

    /**
     * After an event of any slot: the next event a slot in [states] needs so that at most one alert sounds and a
     * held alert waits only while one does. [TimerEvent.AlertHold] for a sounding alert that is not the keeper
     * (preemption, first), [TimerEvent.AlertRingStart] for the held alert released while none sounds (D-109: also
     * while an alert whose sound ended still alerts); null when the slots agree. The controller handles it and
     * settles again, until null.
     */
    fun settle(states: Map<TimerSlot, TimerState>): SlotEvent? {
        val alerts = TimerSlot.entries.mapNotNull { slot -> (states[slot] as? TimerState.Alerting)?.let { slot to it } }
        val sounding = alerts.filter { (_, alert) -> alert.sounds }
        val keeper = sounding.minWithOrNull(
            compareBy<Pair<TimerSlot, TimerState.Alerting>> { (slot, _) -> !slot.isBrew }
                .thenBy { (_, alert) -> alert.ringingSinceElapsedMs },
        )?.first
        val preempted = sounding.firstOrNull { (slot, _) -> slot != keeper }?.first
        return when {
            preempted != null -> SlotEvent(preempted, TimerEvent.AlertHold)

            keeper != null -> null

            else -> alerts.filter { (_, alert) -> alert.held }.minByOrNull { (_, alert) -> alert.deadlineElapsedMs }
                ?.let { (slot, _) -> SlotEvent(slot, TimerEvent.AlertRingStart) }
        }
    }

    /**
     * [eventFor] the quick slot while the brew slot is in [brew]: the rules for the v0.3.0 pair of one brew session
     * and the quick timer.
     */
    fun quickEvent(event: TimerEvent, brew: TimerState): TimerEvent =
        eventFor(TimerSlot.QUICK, event, mapOf(TimerSlot.BREW to brew))

    /**
     * [settle] for the v0.3.0 pair of a brew in [brew] and the quick timer in [quick]: the event the quick slot
     * needs, [TimerEvent.AlertHold] while the brew's alert sounds and the quick alert sounds (preemption),
     * [TimerEvent.AlertRingStart] when the brew's alert no longer sounds and the quick alert is held (release,
     * D-109); null when the two already agree.
     */
    fun settle(brew: TimerState, quick: TimerState): TimerEvent? =
        settle(mapOf(TimerSlot.BREW to brew, TimerSlot.QUICK to quick))?.takeIf { it.slot == TimerSlot.QUICK }?.event

    /** Whether a deadline of [slot] takes the sound from [sounding]'s alert: a brew's from the quick timer's. */
    private fun preempts(slot: TimerSlot, sounding: TimerSlot): Boolean = slot.isBrew && !sounding.isBrew

    /**
     * Whether [this] state's alert sounds now (D-109): alerting, not held, and the player has not reported the end
     * of its sound (`alertPlayed`). A held alert and one whose sound ended hold nothing.
     */
    private val TimerState.sounds: Boolean get() = this is TimerState.Alerting && !held && !alertPlayed
}

/**
 * The one alert player's sound as the controller's arbitration sees it (04 §5, §9.3; D-098, D-108): never two sounds
 * at once, and a cue of one slot never cuts another slot's alert.
 *
 * - An alert ([alertStarted]) takes the player, cutting any pre-alert that still rings, as within one slot. It
 *   ends with the player's report or the slot's `StopAlert` ([alertEnded]).
 * - A `StopAlert` of one slot reaches the player only when no other slot owns the sound ([mayStop]):
 *   the preempted quick alert's stop must not silence the brew's alert, and *Fertig* on a held alert must not
 *   cut the brew's pre-alert.
 * - A pre-alert due while another slot's alert or pre-alert sounds waits ([preAlertDue]) until that sound
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

    /** A pre-alert of [slot] waiting for another slot's sound, for the step that ends at [deadlineElapsedMs]. */
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
     * Whether a `StopAlert` of [slot] at [nowElapsedMs] may stop the player: not while another slot's alert or
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
     * plays now (it then owns the sound), false when it waits for another slot's sound.
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
