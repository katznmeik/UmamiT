package com.umamit.feature.brew.quick

import com.umamit.core.ui.DurationSteps

/**
 * The durations of the quick timer (SCR-07, TMR-22): the chips and its bounds. The picker's rules
 * (5 s – 24 h in the PRF-04 increments) are [DurationSteps] in `core:ui`.
 */
object QuickDuration {
    /** 0:30, 1:00, 1:30, 2:00, 3:00, 5:00, 8:00. */
    val CHIPS: List<Int> = listOf(30, 60, 90, 120, 180, 300, 480)

    /** What SCR-07 opens with: 1:00. */
    const val DEFAULT_SECONDS = 60

    const val MIN_SECONDS = DurationSteps.MIN_SECONDS
    const val MAX_SECONDS = DurationSteps.MAX_SECONDS
}
