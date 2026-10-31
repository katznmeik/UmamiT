package com.umamit.app

import com.umamit.core.designsystem.component.ActiveBrew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the shell reads the running brew from for the active brew bar (GEN-06); the app binds the
 * controller's state (`TimerActiveBrewSource`).
 */
interface ActiveBrewSource {
    /**
     * The running brew session, else the quick timer, or `null` when none runs (the bar is then not shown). With
     * both, the brew session: it comes first in the bar (D-098).
     */
    val activeBrew: StateFlow<ActiveBrew?>

    /**
     * Progress 0..1 of the current step; read only while drawing, so a per-frame value does not recompose.
     * Short steps may change it per frame. For a long step (≥ 10 min, `ActiveBrew.longStep`) it changes
     * only about every `UmamiMotion.longStepUpdateIntervalMillis` (03 §3.6 battery rule): the bar is on
     * every tab for the whole step. `TimerAppWiringTest` checks that the bound source holds still in between.
     */
    val progress: () -> Float

    /** The quick timer while a brew session is active too (D-098): the bar's second half; `null` otherwise. */
    val alongside: StateFlow<ActiveBrew?> get() = NO_ALONGSIDE

    /** The [alongside] timer's progress, as [progress]. */
    val alongsideProgress: () -> Float get() = NO_PROGRESS
}

private val NO_ALONGSIDE: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()

private val NO_PROGRESS: () -> Float = { 0f }

/** No brew runs: for previews and tests of the shell. */
object NoActiveBrewSource : ActiveBrewSource {
    override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()
    override val progress: () -> Float = { 0f }
}
