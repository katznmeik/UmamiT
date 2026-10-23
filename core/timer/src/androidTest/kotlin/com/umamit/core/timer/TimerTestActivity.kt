package com.umamit.core.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Starts a brew session of one step through the real controller, in the main process of the
 * test APK and while it is in the foreground, as *Brühen starten* and *Start* do in the app;
 * then closes. The step has no pre-alert, so its deadline alarm is its only alarm.
 */
class TimerTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
        val coldBrew = intent.getBooleanExtra(EXTRA_COLD_BREW, false)
        val controller = (application as TimerTestApplication).controller
        CoroutineScope(Dispatchers.Main).launch {
            val started = controller.startSession(brew(seconds, coldBrew))
            val running = controller.send(TimerEvent.Start)
            Log.i(TAG, "Step of $seconds s: start $started, Start $running")
            (controller.state.value as? TimerUiState.Active)?.deadlineWallMs?.let {
                deadlineFile(this@TimerTestActivity).writeText(it.toString())
            }
            // The test runs in another process: it reads the outcome here and fails fast on a refusal.
            outcomeFile(this@TimerTestActivity).writeText(
                if (started == TransitionOutcome.Accepted && running == TransitionOutcome.Accepted) {
                    STARTED
                } else {
                    "start $started, Start $running"
                },
            )
            finish()
        }
    }

    companion object {
        private const val TAG = "UmamiTTimerTest"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_COLD_BREW = "coldBrew"

        /** The tea of the test session; the alert notification shows it after the restart. */
        const val TEA = "Testtee"

        /** The outcome when the step started and runs. */
        const val STARTED = "started"

        /** Where the activity writes the outcome of the start (app-private, shared by both processes). */
        fun outcomeFile(context: Context): File = File(context.filesDir, "timer-test-start.txt")

        /** Where the activity writes the running step's deadline on the wall clock. */
        fun deadlineFile(context: Context): File = File(context.filesDir, "timer-test-deadline.txt")

        /** A step of [seconds]; with [coldBrew] every step is a long step without the service (TMR-18). */
        fun intent(context: Context, seconds: Int, coldBrew: Boolean = false): Intent =
            Intent(context, TimerTestActivity::class.java)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_COLD_BREW, coldBrew)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun brew(seconds: Int, coldBrew: Boolean) = BrewStart(
            snapshot = SessionSnapshot(
                teaId = null,
                teaName = TEA,
                teaSecondaryName = null,
                teaType = null,
                styleKey = null,
                category = null,
                profileId = null,
                profileName = null,
                method = if (coldBrew) BrewMethod.COLD_BREW else BrewMethod.KYUSU,
                vesselId = null,
                vesselName = null,
                leafGrams = null,
                waterMl = null,
                baseTempC = 60,
            ),
            steps = listOf(PlannedStep(StepKind.INFUSION, seconds, tempC = 60)),
            recipePreAlertSeconds = 0,
            preAlert = PreAlertChoice.Off,
        )
    }
}
