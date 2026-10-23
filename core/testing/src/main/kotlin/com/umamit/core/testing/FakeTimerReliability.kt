package com.umamit.core.testing

import android.content.Intent
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.core.timer.reliability.TimerReliability

/**
 * In-memory [TimerReliability] (TMR-31, SET-24, ONB-02): every check passes until a test marks
 * one [missing]; [opened] records each check whose page the screens asked for.
 */
class FakeTimerReliability(vararg missing: ReliabilityCheck) : TimerReliability {
    private val states = ReliabilityCheck.entries.associateWith { CheckState.Ok }.toMutableMap()

    /** The checks whose page was opened, in order. */
    val opened = mutableListOf<ReliabilityCheck>()

    init {
        missing.forEach { states[it] = CheckState.Missing }
    }

    /** Sets [check] to [state], as if the user changed it in the system settings. */
    fun set(check: ReliabilityCheck, state: CheckState) {
        states[check] = state
    }

    /** Every check passes. */
    fun grantAll() = ReliabilityCheck.entries.forEach { states[it] = CheckState.Ok }

    override fun status(): ReliabilityStatus = ReliabilityStatus(states.toMap())

    /** Records [check] and returns no page: tests see which page a screen asked for in [opened]. */
    override fun settingsIntents(check: ReliabilityCheck): List<Intent> {
        opened += check
        return emptyList()
    }
}
