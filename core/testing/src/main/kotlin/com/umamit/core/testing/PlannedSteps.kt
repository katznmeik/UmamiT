package com.umamit.core.testing

import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep

/** A rinse of [seconds] at 70 °C, for tests. */
fun rinse(seconds: Int) = PlannedStep(StepKind.RINSE, seconds, tempC = 70)

/** An infusion with its own [preAlert]: null *Automatisch*, 0 *Aus*, else the lead (TMR-23, D-093). */
fun infusion(seconds: Int, tempC: Int? = 60, preAlert: Int? = null) =
    PlannedStep(StepKind.INFUSION, seconds, tempC, preAlert)
