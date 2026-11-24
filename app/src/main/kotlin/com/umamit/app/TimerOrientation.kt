package com.umamit.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavBackStackEntry

/**
 * Reports whether a timer screen is the top route [entry] ([isTimerScreen]: SCR-05, SCR-07) or a notification's
 * request for the timer is [requestPending], for the activity's portrait request (D-136 Q5, build note „Portrait
 * timer"). From the back stack, so it changes when navigation starts; a close over the keyguard does not end it, only
 * the pop of the timer at `ON_STOP` does, so the timer never turns while the keyguard comes back.
 */
@Composable
internal fun ReportTimerScreenOnTop(entry: NavBackStackEntry?, requestPending: Boolean, onChange: (Boolean) -> Unit) {
    val onTop = entry?.destination?.isTimerScreen() == true || requestPending
    val currentOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(onTop) { currentOnChange(onTop) }
}

/**
 * The orientation the activity asks for (D-136 Q5, build note „Portrait timer"): portrait while a timer screen is on
 * top or about to be, else the system's choice, which the manifest leaves it (no `screenOrientation`).
 */
internal fun screenOrientationFor(timerScreenOnTop: Boolean): Int = if (timerScreenOnTop) {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
} else {
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/**
 * Asks for [screenOrientationFor] [timerScreenOnTop]; only a change is sent. [MainActivity] handles the rotation this
 * causes in place (`configChanges`), so it re-creates nothing.
 */
// A locked orientation is what SCR-05/SCR-07 ask for, and only while a timer screen is on top; where Android ignores it
// (large screens, multi-window, a user override) the timer rotates in place, as best effort (NFR-COMP-02).
@SuppressLint("SourceLockedOrientationActivity")
internal fun Activity.requestTimerOrientation(timerScreenOnTop: Boolean) {
    val orientation = screenOrientationFor(timerScreenOnTop)
    if (requestedOrientation != orientation) requestedOrientation = orientation
}
