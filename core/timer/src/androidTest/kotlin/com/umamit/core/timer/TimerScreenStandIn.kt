package com.umamit.core.timer

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.umamit.core.timer.notification.TimerScreenIntents

/**
 * The timer screen of the test APK: like the app's activity it declares
 * [TimerScreenIntents.ACTION_OPEN_TIMER], so the alert's full-screen intent opens it, and it
 * shows over the lock screen and turns the screen on, set from code before its first frame
 * (04 §8.2, §13). The app's own timer route is tested in the app's instrumented tests.
 */
class TimerScreenStandIn : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(TextView(this).apply { text = "Timer" })
    }
}
