package com.umamit.core.timer.probe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.umamit.core.timer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The probe's full-screen alert „Jetzt abgießen!" (04 §8.2), launched by the
 * full-screen intent of the alert notification. It turns the screen on and
 * shows over the lock screen; both are set from code only while it is shown
 * (04 §13), so no other screen of the app appears over the lock screen.
 * *Abgegossen* stops the step. Visual only; sound and vibration come from the
 * AlertPlayer (#16).
 */
class ProbeAlertActivity : Activity() {
    private var scope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_probe_alert)
        findViewById<Button>(R.id.probe_alert_done).setOnClickListener {
            ProbeTimer.stop(this)
            finish()
        }
        // Close when the step is stopped elsewhere (notification action, end of the alert window).
        scope = MainScope().also { scope ->
            scope.launch {
                ProbeTimer.status.first { it is ProbeStatus.Idle }
                finish()
            }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        super.onDestroy()
    }

    internal companion object {
        fun intent(context: Context): Intent = Intent(context, ProbeAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
