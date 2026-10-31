package com.umamit.core.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.state
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The user opens the app again after a kill (#46, 04 §7.2): this stands for the timer screen.
 * It shows what the controller holds once the process start recovered it, written to a file
 * for the test in the other process: the phase, the deadline on the wall clock and the tea;
 * then it closes. With two timers (D-098) it also writes each slot's phase and deadline
 * ([slotsFile]), before the [stateFile] the test waits for.
 */
class TimerStateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as TimerTestApplication).controller
        CoroutineScope(Dispatchers.Main).launch {
            val state = withTimeoutOrNull(STATE_TIMEOUT_MS) {
                controller.state.first { it is TimerUiState.Active }
            } as? TimerUiState.Active
            // Recovery restores both slots in one turn and only then counts as done.
            withTimeoutOrNull(STATE_TIMEOUT_MS) { controller.recovered.first { it } }
            slotsFile(this@TimerStateActivity).writeTextAtomically(
                TimerSlot.entries.joinToString("\n") { slot ->
                    val active = controller.state(slot).value as? TimerUiState.Active
                    "$slot ${active?.let { "${it.phase}|${it.deadlineWallMs}" } ?: IDLE}"
                },
            )
            stateFile(this@TimerStateActivity).writeTextAtomically(
                state?.let { "${it.phase}|${it.deadlineWallMs}|${it.teaName}" } ?: IDLE,
            )
            finish()
        }
    }

    companion object {
        private const val STATE_TIMEOUT_MS = 10_000L

        /** What the screen shows when the process start recovered nothing. */
        const val IDLE = "IDLE"

        /** Where the activity writes the state it shows (app-private, shared by both processes). */
        fun stateFile(context: Context): File = File(context.filesDir, "timer-test-state.txt")

        /** Where it writes each slot's state, one line each: `BREW RUNNING|<deadline>`, `QUICK IDLE`. */
        fun slotsFile(context: Context): File = File(context.filesDir, "timer-test-slots.txt")

        fun intent(context: Context): Intent = Intent(context, TimerStateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
