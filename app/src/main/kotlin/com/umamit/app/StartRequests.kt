package com.umamit.app

import android.os.SystemClock
import com.umamit.core.data.reminder.OpenStartIntents

/**
 * The taps on the daily low-stock reminder (INV-11, 03 §2.4, D-098), as [MainActivity] keeps them: a new
 * token per tap, pending until the root has shown Start for it (or let it go, before the setup is done). A
 * pending tap survives the activity's recreation; a task reopened from the recent apps brings its first
 * intent again, which was handled then.
 *
 * No gate as the catalog's taps have (#121): the action only opens Start, which any app can show by
 * starting the launcher activity anyway.
 */
class StartRequests(newToken: () -> Long = SystemClock::elapsedRealtimeNanos) :
    IntentRequests(KEY_PENDING, OpenStartIntents::isOpenStart, newToken)

private const val KEY_PENDING = "com.umamit.app.PENDING_START_REQUEST"
