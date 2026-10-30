package com.umamit.core.timer.controller

import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.notification.runsWithService

/**
 * Which notification each active timer has while a brew session and a quick timer run at once (04 §8.2,
 * D-098): the [lead] slot's is the service's (the Live Update 1, the alert 2) or, without the service, the
 * quiet 4; the other active timer's is notification 5 on the LOW channel `timer_other`, so the alert stays the
 * app's only notification of importance DEFAULT or higher (D-044). The lead is the ringing slot (the brew's
 * first), else the first slot, brew before quick, that runs with the service, else the first active one. With
 * one timer every effect goes to the service and the notifier as before v0.3.0. Which timer is the Live Update
 * (the one due next, and its promotion) is v0.3.0-04's. Used inside the controller's event lock.
 */
internal class NotificationRoles(
    private val service: TimerServicePort,
    private val notifier: TimerNotifierPort,
    private val screens: SlotScreens,
) {
    /** The slot whose notification the service shows (1, 2) or the quiet one (4); null while both are idle. */
    var lead: TimerSlot? = null
        private set

    /** The slot shown as notification 5; null when none is. */
    private var otherShown: TimerSlot? = null

    /** The current turn sent the service a command (`StartForeground`, `StopForeground`). */
    var serviceCommanded = false

    /** A transition of [slot] begins, the screens already showing its new state: the roles after it. */
    fun begin(slot: TimerSlot): Transition {
        val before = lead
        lead = leadNow()
        return Transition(slot, before, alone = screens[slot.other] == null && otherShown == null)
    }

    private fun leadNow(): TimerSlot? {
        val active = TimerSlot.entries.mapNotNull { slot -> screens[slot]?.let { slot to it } }
        return active.firstOrNull { it.second.ringing }?.first
            ?: active.firstOrNull { it.second.runsWithService }?.first
            ?: active.firstOrNull()?.first
    }

    /** Notification 5 shows [state], the active timer that is not the [lead]; null removes it. */
    private fun showOther(state: TimerUiState.Active?) {
        otherShown = state?.slot
        notifier.showOther(state)
    }

    /**
     * The service and notification effects of one transition of [slot]. [alone]: the other slot has no timer,
     * so every effect goes where it went before v0.3.0.
     */
    inner class Transition(
        private val slot: TimerSlot,
        private val leadBefore: TimerSlot?,
        private val alone: Boolean,
    ) {
        private var shownAsOther = false

        private val leads: Boolean get() = alone || lead == slot

        /** `StartForeground`: only the lead's state is the service's notification. */
        fun startForeground(state: TimerUiState.Active, fullScreen: Boolean) {
            if (!leads) return
            serviceCommanded = true
            service.startForeground(state, fullScreen)
        }

        /** `StopForeground`: while the other slot leads, it needs the service, which keeps running (04 §6.1). */
        fun stopForeground() {
            if (!leads) return
            serviceCommanded = true
            service.stopForeground()
        }

        /** `UpdateNotification`: the lead's goes to the notifier, the other's is notification 5. */
        fun update(state: TimerUiState.Active, fullScreen: Boolean) {
            if (leads) {
                notifier.update(state, fullScreen)
            } else {
                showOther(state)
                shownAsOther = true
            }
        }

        /**
         * After the transition's effects, while the other slot has a timer: a slot that took the lead from the
         * other moves the other to notification 5; a slot that lost it hands the service to the other (or stops
         * it, and the other shows quietly) and shows as notification 5 itself; a slot that went idle takes its
         * notification 5 with it.
         */
        fun finish() {
            if (alone) return
            val mine = screens[slot]
            val theirs = screens[slot.other]
            if (lead == slot && leadBefore == slot.other && theirs != null) showOther(theirs)
            if (lead == slot.other && leadBefore != slot.other && theirs != null) handOver(theirs)
            when {
                mine == null -> if (otherShown == slot) showOther(null)
                lead != slot && !shownAsOther -> showOther(mine)
            }
        }

        /** The other slot's timer [theirs] takes the lead from this one. */
        private fun handOver(theirs: TimerUiState.Active) {
            // A held alert is released in this very turn and takes the service with its own alert then.
            if (!theirs.alertHeld) {
                serviceCommanded = true
                if (theirs.runsWithService) {
                    service.startForeground(theirs, fullScreen = false)
                } else {
                    service.stopForeground()
                    notifier.update(theirs, fullScreen = false)
                }
            }
            if (otherShown == slot.other) showOther(null)
        }
    }
}
