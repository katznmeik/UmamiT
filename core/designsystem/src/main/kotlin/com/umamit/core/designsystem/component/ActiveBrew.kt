package com.umamit.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * What the [ActiveBrewBar] shows about the running session or quick timer. The
 * step's progress is passed separately, as a lambda read only while drawing.
 *
 * @param title the tea name, or „Schnelltimer".
 * @param step „Aufguss 2/4"; `null` for a quick timer.
 * @param stepDescription the step for TalkBack („Aufguss 2 von 4"); defaults to [step].
 */
@Immutable
data class ActiveBrew(
    val title: String,
    val step: String?,
    val remainingSeconds: Int,
    val liquorColor: Color,
    val stepDescription: String? = step,
)
