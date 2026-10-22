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
        val controller = (application as TimerTestApplication).controller
        CoroutineScope(Dispatchers.Main).launch {
            val started = controller.startSession(brew(seconds))
            val running = controller.send(TimerEvent.Start)
            Log.i(TAG, "Step of $seconds s: start $started, Start $running")
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

        /** The tea of the test session; the alert notification shows it after the restart. */
        const val TEA = "Testtee"

        /** The outcome when the step started and runs. */
        const val STARTED = "started"

        /** Where the activity writes the outcome of the start (app-private, shared by both processes). */
        fun outcomeFile(context: Context): File = File(context.filesDir, "timer-test-start.txt")

        fun intent(context: Context, seconds: Int): Intent = Intent(context, TimerTestActivity::class.java)
            .putExtra(EXTRA_SECONDS, seconds)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun brew(seconds: Int) = BrewStart(
            snapshot = SessionSnapshot(
                teaId = null,
                teaName = TEA,
                teaSecondaryName = null,
                teaType = null,
                styleKey = null,
                category = null,
                profileId = null,
                profileName = null,
                method = BrewMethod.KYUSU,
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
