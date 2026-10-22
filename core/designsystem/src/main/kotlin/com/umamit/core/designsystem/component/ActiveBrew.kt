package com.umamit.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * What the [ActiveBrewBar] shows about the running session or quick timer.
 *
 * @param title the tea name, or „Schnelltimer".
 * @param step „Aufguss 2/4"; `null` for a quick timer.
 * @param stepDescription the step for TalkBack („Aufguss 2 von 4"); defaults to [step].
 * @param progress elapsed fraction of the current step, 0 to 1.
 */
@Immutable
data class ActiveBrew(
    val title: String,
    val step: String?,
    val remainingSeconds: Int,
    val progress: Float,
    val liquorColor: Color,
    val stepDescription: String? = step,
)
