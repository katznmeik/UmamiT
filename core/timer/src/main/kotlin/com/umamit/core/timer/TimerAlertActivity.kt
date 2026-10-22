package com.umamit.core.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/**
 * The full-screen alert „Jetzt abgießen!" of the real timer (04 §8.2), the
 * target of the alert notification's full-screen intent until SCR-05 takes it
 * over (M1-11, #47; D-057). It turns the screen on and shows over the lock
 * screen, set from code only while it is shown (04 §13). Visual only: the
 * AlertPlayer plays sound and vibration; *Schließen* closes it.
 */
class TimerAlertActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_timer_alert)
        findViewById<TextView>(R.id.timer_alert_subtitle).text = intent.getStringExtra(EXTRA_SUBTITLE)
        findViewById<Button>(R.id.timer_alert_close).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        super.onDestroy()
    }

    internal companion object {
        private const val EXTRA_SUBTITLE = "com.umamit.core.timer.extra.ALERT_SUBTITLE"

        /** Opens the alert for the tea [subtitle] (or „Schnelltimer"). */
        fun intent(context: Context, subtitle: String): Intent = Intent(context, TimerAlertActivity::class.java)
            .putExtra(EXTRA_SUBTITLE, subtitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
