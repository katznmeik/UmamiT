package com.umamit.app

import com.umamit.core.designsystem.component.ActiveBrew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the shell reads the running brew from for the active brew bar (GEN-06); M1-09 binds the timer session. */
interface ActiveBrewSource {
    /** The running brew, or `null` when none runs (the bar is then not shown). */
    val activeBrew: StateFlow<ActiveBrew?>

    /** Progress 0..1 of the current step; read only while drawing, so a per-frame value does not recompose. */
    val progress: () -> Float
}

/** No brew runs: the source until M1-09. */
object NoActiveBrewSource : ActiveBrewSource {
    override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()
    override val progress: () -> Float = { 0f }
}
