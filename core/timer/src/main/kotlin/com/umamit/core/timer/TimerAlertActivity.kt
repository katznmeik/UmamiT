package com.umamit.core.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The full-screen alert „Jetzt abgießen!" of the real timer (04 §8.2), the
 * target of the alert notification's full-screen intent until SCR-05 takes it
 * over (M1-11, #47; D-057). It turns the screen on and shows over the lock
 * screen, set from code only while it is shown (04 §13). *Abgegossen* sends
 * `poured` to the controller ([TimerHost]), which stops the alert and moves on
 * to the next step, and closes the screen; sound and vibration come from the
 * AlertPlayer.
 */
class TimerAlertActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_timer_alert)
        showSubtitle(intent)
        findViewById<Button>(R.id.timer_alert_done).setOnClickListener { poured() }
    }

    /** A later alert reuses this screen (`singleTask`): it shows that alert's tea. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showSubtitle(intent)
    }

    override fun onDestroy() {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        super.onDestroy()
    }

    private fun showSubtitle(intent: Intent) {
        findViewById<TextView>(R.id.timer_alert_subtitle).text = intent.getStringExtra(EXTRA_SUBTITLE)
    }

    private fun poured() {
        val inputs = (application as? TimerHost)?.timerInputs
        if (inputs == null) {
            Log.e(TAG, "Abgegossen without the timer controller (TimerHost)")
        } else {
            // The turn outlives this screen; once it holds the controller's lock it completes anyway.
            scope.launch { inputs.send(TimerEvent.Poured, EventSource.APP) }
        }
        finish()
    }

    internal companion object {
        private const val TAG = "UmamiTAlert"
        private const val EXTRA_SUBTITLE = "com.umamit.core.timer.extra.ALERT_SUBTITLE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Opens the alert for the tea [subtitle] (or „Schnelltimer"). */
        fun intent(context: Context, subtitle: String): Intent = Intent(context, TimerAlertActivity::class.java)
            .putExtra(EXTRA_SUBTITLE, subtitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
