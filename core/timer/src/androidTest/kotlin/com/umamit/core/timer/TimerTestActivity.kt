package com.umamit.core.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.state
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Starts a brew session through the real controller, in the main process of the test APK and
 * while it is in the foreground, as *Brühen starten* and *Start* do in the app; then closes.
 * By default the session has one step without a pre-alert, so its deadline alarm is its only
 * alarm; the accuracy run (#52) starts sessions of several steps with a pre-alert. A quick timer
 * can run alongside (D-098), started after the brew's step, or alone beside a brew that runs
 * already ([quickIntent]), as Start's *Schnelltimer* starts it.
 */
class TimerTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
        val coldBrew = intent.getBooleanExtra(EXTRA_COLD_BREW, false)
        val steps = intent.getIntExtra(EXTRA_STEPS, 1)
        val preAlertSeconds = intent.getIntExtra(EXTRA_PRE_ALERT, 0)
        val quickSeconds = intent.getIntExtra(EXTRA_QUICK_SECONDS, 0)
        val quickPreAlertSeconds = intent.getIntExtra(EXTRA_QUICK_PRE_ALERT, 0)
        val quickRestart = intent.getBooleanExtra(EXTRA_QUICK_RESTART, false)
        val controller = (application as TimerTestApplication).controller
        CoroutineScope(Dispatchers.Main).launch {
            val outcomes = mutableListOf<Pair<String, TransitionOutcome>>()
            if (seconds > 0) {
                val started = controller.startSession(brew(seconds, coldBrew, steps, preAlertSeconds))
                val running = controller.send(TimerSlot.BREW, TimerEvent.Start)
                Log.i(TAG, "Step of $seconds s: start $started, Start $running")
                outcomes += "start" to started
                outcomes += "Start" to running
                deadlineOf(controller, TimerSlot.BREW)?.let {
                    deadlineFile(this@TimerTestActivity).writeTextAtomically(it.toString())
                }
            }
            // A quick timer running alongside (D-098); with quickRestart it replaces the active one, as
            // „Schnelltimer beenden und neu starten?" does, and the brew is never touched.
            if (quickSeconds > 0) {
                val quick = controller.startQuickTimer(
                    quickSeconds,
                    preAlertSeconds = quickPreAlertSeconds,
                    run = true,
                    finishActive = quickRestart,
                )
                Log.i(TAG, "Quick timer of $quickSeconds s, pre-alert $quickPreAlertSeconds s: $quick")
                outcomes += "quick timer" to quick
                deadlineOf(controller, TimerSlot.QUICK)?.let {
                    quickDeadlineFile(this@TimerTestActivity).writeTextAtomically(it.toString())
                }
            }
            // The test runs in another process: it reads the outcome here and fails fast on a refusal.
            outcomeFile(this@TimerTestActivity).writeTextAtomically(
                if (outcomes.all { (_, outcome) -> outcome == TransitionOutcome.Accepted }) {
                    STARTED
                } else {
                    outcomes.joinToString(", ") { (what, outcome) -> "$what $outcome" }
                },
            )
            finish()
        }
    }

    private fun deadlineOf(controller: BrewSessionController, slot: TimerSlot): Long? =
        (controller.state(slot).value as? TimerUiState.Active)?.deadlineWallMs

    companion object {
        private const val TAG = "UmamiTTimerTest"
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_COLD_BREW = "coldBrew"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_PRE_ALERT = "preAlertSeconds"
        private const val EXTRA_QUICK_SECONDS = "quickSeconds"
        private const val EXTRA_QUICK_PRE_ALERT = "quickPreAlertSeconds"
        private const val EXTRA_QUICK_RESTART = "quickRestart"

        /** The tea of the test session; the alert notification shows it after the restart. */
        const val TEA = "Testtee"

        /** The outcome when the step started and runs. */
        const val STARTED = "started"

        /** Where the activity writes the outcome of the start (app-private, shared by both processes). */
        fun outcomeFile(context: Context): File = File(context.filesDir, "timer-test-start.txt")

        /** Where the activity writes the running step's deadline on the wall clock. */
        fun deadlineFile(context: Context): File = File(context.filesDir, "timer-test-deadline.txt")

        /** Where the activity writes the running quick timer's deadline on the wall clock. */
        fun quickDeadlineFile(context: Context): File = File(context.filesDir, "timer-test-quick-deadline.txt")

        /**
         * A session of [steps] infusions of [seconds] each, with a pre-alert of [preAlertSeconds]
         * (0: none); with [coldBrew] every step is a long step without the service (TMR-18). With
         * [quickSeconds] a quick timer of that length runs alongside, started after the brew's step
         * (0: none; D-098), with its own *Vorwarnung* [quickPreAlertSeconds] (0: *Aus*); with
         * [quickRestart] it replaces an active quick timer. [seconds] 0: no brew session, only the
         * quick timer ([quickIntent]).
         */
        fun intent(
            context: Context,
            seconds: Int,
            coldBrew: Boolean = false,
            steps: Int = 1,
            preAlertSeconds: Int = 0,
            quickSeconds: Int = 0,
            quickPreAlertSeconds: Int = 0,
            quickRestart: Boolean = false,
        ): Intent = Intent(context, TimerTestActivity::class.java)
            .putExtra(EXTRA_SECONDS, seconds)
            .putExtra(EXTRA_COLD_BREW, coldBrew)
            .putExtra(EXTRA_STEPS, steps)
            .putExtra(EXTRA_PRE_ALERT, preAlertSeconds)
            .putExtra(EXTRA_QUICK_SECONDS, quickSeconds)
            .putExtra(EXTRA_QUICK_PRE_ALERT, quickPreAlertSeconds)
            .putExtra(EXTRA_QUICK_RESTART, quickRestart)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /**
         * Only a quick timer of [seconds] with the *Vorwarnung* [preAlertSeconds] (0: *Aus*), beside the brew
         * session that runs, as Start's *Schnelltimer* starts it (D-098); with [restart] it replaces the
         * active quick timer („Schnelltimer beenden und neu starten?").
         */
        fun quickIntent(context: Context, seconds: Int, preAlertSeconds: Int = 0, restart: Boolean = false): Intent =
            intent(
                context,
                seconds = 0,
                quickSeconds = seconds,
                quickPreAlertSeconds = preAlertSeconds,
                quickRestart = restart,
            )

        private fun brew(seconds: Int, coldBrew: Boolean, steps: Int, preAlertSeconds: Int) = BrewStart(
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
            // Each step's own explicit pre-alert, 0 = Aus (TMR-23, D-093).
            steps = List(steps) {
                PlannedStep(StepKind.INFUSION, seconds, tempC = 60, preAlertSeconds = preAlertSeconds)
            },
            recipePreAlertSeconds = 0,
        )
    }
}
