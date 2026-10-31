package com.umamit.core.timer.controller

import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.notification.runsWithService

/**
 * Which notification each active timer has (04 §8.2, §8.3; D-044, D-098, PO Q5). With one timer every effect
 * goes to the service and the notifier as before v0.3.0. With a brew session and a quick timer at once, the
 * [lead] is the timer that ends next ([dueNext]): its notification is the service's (the Live Update 1, the
 * alert 2) while either timer needs the service, else the quiet 4; the other active timer is notification 5 on
 * the LOW channel `timer_other`, never promoted, so the alert stays the app's only notification of importance
 * DEFAULT or higher (D-044). The roles are settled after each transition of either timer ([Transition.finish]):
 * notification 5 first, then the lead's, so a move of the Live Update updates 1 and 5 in that turn and the
 * service keeps its foreground ID 1 for it. Used inside the controller's event lock.
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

    /** The service was asked to run: a `StartForeground` since the last `StopForeground`. */
    private var serviceOn = false

    /** The current turn sent the service a command (`StartForeground`, `StopForeground`). */
    var serviceCommanded = false

    /** A transition of [slot] begins, the screens already showing its new state: the roles after it. */
    fun begin(slot: TimerSlot): Transition {
        val before = lead
        lead = dueNext(screens[TimerSlot.BREW], screens[TimerSlot.QUICK])
        return Transition(slot, before, alone = screens[slot.other] == null && otherShown == null)
    }

    /**
     * After the recovery of a new process (04 §7.2): a notification 5 the killed process left behind goes when
     * no timer is shown there now, so it neither outlives its timer nor repeats the Live Update.
     */
    fun dropStaleOther() {
        if (otherShown == null) notifier.showOther(null)
    }

    private fun startService(state: TimerUiState.Active, fullScreen: Boolean) {
        serviceCommanded = true
        serviceOn = true
        service.startForeground(state, fullScreen)
    }

    private fun stopService() {
        serviceCommanded = true
        serviceOn = false
        service.stopForeground()
    }

    /** Notification 5 shows [state], the active timer that is not the [lead]; null removes it. */
    private fun showOther(state: TimerUiState.Active?) {
        otherShown = state?.slot
        notifier.showOther(state)
    }

    /**
     * The service and notification effects of one transition of [slot]. [alone]: the other slot has no timer
     * and none is shown as notification 5, so every effect goes where it went before v0.3.0; else the effects
     * only say what the transition asks for, and [finish] settles both timers' notifications.
     */
    inner class Transition(
        private val slot: TimerSlot,
        private val leadBefore: TimerSlot?,
        private val alone: Boolean,
    ) {
        private var startAsked = false
        private var updateAsked = false

        /** The transition starts its alert: its alert notification takes the screen (D-057). */
        private var fullScreen = false

        /** `StartForeground`. */
        fun startForeground(state: TimerUiState.Active, fullScreen: Boolean) {
            if (alone) return startService(state, fullScreen)
            startAsked = true
            this.fullScreen = this.fullScreen || fullScreen
        }

        /** `StopForeground`: with two timers the service stops only when neither needs it ([finish]). */
        fun stopForeground() {
            if (alone) stopService()
        }

        /** `UpdateNotification`. */
        fun update(state: TimerUiState.Active, fullScreen: Boolean) {
            if (alone) return notifier.update(state, fullScreen)
            updateAsked = true
            this.fullScreen = this.fullScreen || fullScreen
        }

        /**
         * After the transition's effects, with two timers: notification 5 shows the timer that is not the lead,
         * or goes; then the lead's notification is the service's while either timer needs the service (04 §6.1),
         * sent again when the lead moved, the service was off or the transition asked for it, else the quiet one.
         * A slot that went idle takes its notification 5 with it; the last one to need the service stops it.
         */
        fun finish() {
            if (alone) return
            val leading = lead?.let { screens[it] }
            val other = lead?.other?.let { screens[it] }
            when {
                other == null -> if (otherShown != null) showOther(null)
                otherShown != other.slot || other.slot == slot -> showOther(other)
            }
            val needsService = leading?.runsWithService == true || other?.runsWithService == true
            when {
                leading != null -> showLead(leading, needsService)
                serviceOn -> stopService()
            }
        }

        /** The lead's notification [leading]: the service's while either timer [needsService], else the quiet one. */
        private fun showLead(leading: TimerUiState.Active, needsService: Boolean) {
            val own = lead == slot
            val moved = leadBefore != lead
            when {
                // A held alert is released in this very turn and takes the service with its own alert then.
                leading.alertHeld -> Unit

                !needsService -> showQuiet(leading, again = moved || (own && updateAsked))

                moved || (own && startAsked) || !serviceOn -> startService(leading, own && fullScreen)

                own && updateAsked -> notifier.update(leading, fullScreen)
            }
        }

        /** Neither timer needs the service: it stops, and the lead shows quietly (4; TMR-14, TMR-18). */
        private fun showQuiet(leading: TimerUiState.Active, again: Boolean) {
            if (serviceOn) {
                stopService()
                notifier.update(leading, fullScreen = false)
            } else if (again) {
                notifier.update(leading, fullScreen = false)
            }
        }
    }
}

/**
 * The slot whose timer ends next (04 §8.3, D-098, PO Q5), the Live Update's: the ringing alert (the brew's
 * first); else a running timer before a waiting one; of two running, the earlier deadline (the brew on a tie);
 * of two waiting, the brew, unless it went quiet (TMR-14) and the quick timer has not. Null while both are idle.
 */
internal fun dueNext(brew: TimerUiState.Active?, quick: TimerUiState.Active?): TimerSlot? {
    val active = listOfNotNull(brew, quick)
    val running = active.filter { it.phase == TimerPhase.RUNNING }
    return (
        active.firstOrNull { it.ringing }
            ?: running.minByOrNull { checkNotNull(it.deadlineElapsedMs) }
            ?: active.firstOrNull { !it.demoted }
            ?: active.firstOrNull()
        )?.slot
}
