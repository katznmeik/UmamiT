package com.umamit.app

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.umamit.core.model.TimerSlot

/**
 * A tap on the row for the [other] timer on the timer [route] of [entry] (D-098): the other timer's route above it;
 * from a route opened by that row, back to the route below it, so two timers never stack more than two routes. Only
 * while resumed: a quick double tap opens the other timer once (D-090).
 */
internal fun NavHostController.showOtherTimer(entry: NavBackStackEntry, route: TimerRoute, other: TimerSlot) {
    if (entry.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (route.switched) popBackStack() else navigate(TimerRoute(other, switched = true))
}

/**
 * Pops back to the timer route that shows [slot]'s timer (a notification opens its own timer, 04 §8.2, D-098); null:
 * the topmost timer route. A topmost route that shows the other timer is left as its row leaves it: back to the route
 * below when the row opened it ([TimerRoute.switched]), else [slot]'s route opens above it, so two timers never stack
 * more than two routes. False when no timer route is on the back stack.
 */
internal fun NavHostController.popBackToTimerOf(slot: TimerSlot?): Boolean {
    val onStack = hasTimer()
    if (onStack) {
        popBackStack<TimerRoute>(inclusive = false)
        currentBackStackEntry?.let { top -> showFrom(top, slot) }
    }
    return onStack
}

/** From the timer route [top] on top, [slot]'s timer (null: whichever [top] shows), as [popBackToTimerOf] says. */
private fun NavHostController.showFrom(top: NavBackStackEntry, slot: TimerSlot?) {
    val route = top.toRoute<TimerRoute>()
    val shows = route.slot ?: top.savedStateHandle.get<String>(PINNED_SLOT_KEY)?.let { name ->
        TimerSlot.entries.firstOrNull { it.name == name }
    }
    when {
        slot == null || shows == null || shows == slot -> Unit
        route.switched -> popBackStack()
        else -> navigate(TimerRoute(slot, switched = true))
    }
}

/** A timer route opened for no timer keeps [slot]'s from now on: [popBackToTimerOf] reads it (04 §8.2). */
internal fun NavBackStackEntry.pinTimer(slot: TimerSlot) {
    savedStateHandle[PINNED_SLOT_KEY] = slot.name
}

/** Whether the timer is on the back stack (its public check throws when it is not). */
internal fun NavHostController.hasTimer(): Boolean = try {
    getBackStackEntry<TimerRoute>()
    true
} catch (_: IllegalArgumentException) {
    false
}

/** The timer a timer route opened for none shows, in its entry's saved state ([pinTimer]). */
private const val PINNED_SLOT_KEY = "com.umamit.app.PINNED_TIMER_SLOT"
