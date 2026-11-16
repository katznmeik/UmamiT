package com.umamit.core.timer.reliability

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reliability checks of a screen that lists them, the setup page (ONB-02) and the settings (SET-24): their
 * [status] as first read when it is made, read again by [refresh] (on every resume and after the notification
 * permission dialog), and [open], a check's system pages for the screen to start. Each screen keeps its own rows and
 * their texts.
 */
class ReliabilityChecks(private val reliability: TimerReliability) {
    private val checks = MutableStateFlow(reliability.status())

    /** The checks as last read. */
    val status: StateFlow<ReliabilityStatus> = checks.asStateFlow()

    /** Reads the checks again. */
    fun refresh() {
        checks.value = reliability.status()
    }

    /** The system pages of [check]. */
    fun open(check: ReliabilityCheck): List<Intent> = reliability.settingsIntents(check)
}
