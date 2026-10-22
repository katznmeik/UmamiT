package com.umamit.core.timer

import android.app.Application
import com.umamit.core.common.SystemWallClock
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.CoroutineTimerWakeUp
import com.umamit.core.timer.controller.TimerAlarmSink
import com.umamit.core.timer.controller.TimerAlarmSinkHost
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerPorts
import com.umamit.core.timer.service.BrewTimerServicePort

/**
 * The app of the timer's instrumented test APK (#42, D-057): the real controller with the
 * real alarm adapter, service port, database and clocks, wired by hand as the app's Hilt
 * graph will be once every port has its adapter. The ports of later tickets are
 * [LogcatTimerPorts]; the settings are the defaults ([DefaultSettings]).
 *
 * Hilt is not used here: `@HiltAndroidApp` must sit in an application module. The test
 * runner's own process creates this app too, but never builds the controller.
 */
class TimerTestApplication :
    Application(),
    TimerAlarmSinkHost {
    val controller: BrewSessionController by lazy { buildController() }

    override val timerAlarmSink: TimerAlarmSink get() = controller

    private fun buildController(): BrewSessionController {
        val db = umamiDatabaseBuilder(this).build()
        val settings = DefaultSettings()
        val clocks = TimerClocks(AndroidMonotonicClock(this), SystemWallClock)
        val logcat = LogcatTimerPorts()
        val ports = TimerPorts(
            alarms = AndroidTimerAlarms(this, clocks, logcat),
            service = BrewTimerServicePort(this, clocks, logcat, TimerStateNotifications(this)),
            notifier = logcat,
            alert = logcat,
            diagnostics = logcat,
            savedSheet = logcat,
            wakeUp = CoroutineTimerWakeUp(),
        )
        return BrewSessionController(
            clocks = clocks,
            sessions = RoomSessionRepository(db, settings, SystemWallClock),
            activeBrew = RoomActiveBrewStore(db, SystemWallClock),
            transactions = RoomDatabaseTransactions(db),
            settings = settings,
            ports = ports,
        )
    }
}
