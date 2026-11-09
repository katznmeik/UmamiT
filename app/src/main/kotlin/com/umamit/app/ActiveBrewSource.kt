package com.umamit.app

import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the shell reads the running brew from for the active brew bar (GEN-06); the app binds the
 * controller's state (`TimerActiveBrewSource`).
 */
interface ActiveBrewSource {
    /**
     * The first active timer in the slot order (`BREW`, `BREW_2`, `QUICK`), or `null` when none runs (the bar is
     * then not shown): with two timers it comes first in the bar, a brew before the quick timer (D-098, D-108).
     */
    val activeBrew: StateFlow<ActiveBrew?>

    /**
     * Progress 0..1 of the current step; read only while drawing, so a per-frame value does not recompose.
     * Short steps may change it per frame. For a long step (≥ 60 min or cold/ice brew, `ActiveBrew.longStep`) it
     * changes only about every `UmamiMotion.longStepUpdateIntervalMillis` (03 §3.6 battery rule): the bar is on
     * every tab for the whole step. `TimerAppWiringTest` checks that the bound source holds still in between.
     */
    val progress: () -> Float

    /**
     * The second active timer in the slot order: the quick timer beside a brew session (D-098) or the second brew
     * session (D-108), the bar's second half; `null` otherwise.
     */
    val alongside: StateFlow<ActiveBrew?> get() = NO_ALONGSIDE

    /** The [alongside] timer's progress, as [progress]. */
    val alongsideProgress: () -> Float get() = NO_PROGRESS

    /**
     * The slots of [activeBrew] and [alongside], in that order (empty while none runs): a tap on a half opens that
     * slot's timer (D-098, D-108).
     */
    val barSlots: StateFlow<List<TimerSlot>> get() = BREW_AND_QUICK
}

private val NO_ALONGSIDE: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()

private val BREW_AND_QUICK: StateFlow<List<TimerSlot>> =
    MutableStateFlow(listOf(TimerSlot.BREW, TimerSlot.QUICK)).asStateFlow()

private val NO_PROGRESS: () -> Float = { 0f }

/** No brew runs: for previews and tests of the shell. */
object NoActiveBrewSource : ActiveBrewSource {
    override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()
    override val progress: () -> Float = { 0f }
}
