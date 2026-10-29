package com.umamit.feature.brew.setup

import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.steps.SaveToRecipeRequest

/** What a tap on the setup does (SCR-04). */
class BrewSetupActions(
    val back: () -> Unit = {},
    val selectRecipe: (String) -> Unit = {},
    val selectVessel: (String) -> Unit = {},
    val stepWater: (up: Boolean) -> Unit = {},
    val stepLeaf: (up: Boolean) -> Unit = {},
    val setRatioLocked: (Boolean) -> Unit = {},
    val applySteps: (TimerEvent.EditSteps) -> Unit = {},
    val saveStepsToRecipe: (SaveToRecipeRequest) -> Unit = {},
    val saveToRecipe: () -> Unit = {},
    val start: () -> Unit = {},
    val confirmRestart: () -> Unit = {},
    val dismissQuestion: () -> Unit = {},
    /** *Speichern* in „Wie viel ist noch da?": the tea's new total in g (BRW-02, INV-03). */
    val correctStock: (Double) -> Unit = {},
)
