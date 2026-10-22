package com.umamit.core.timer

import android.app.Application
import com.umamit.core.common.SystemWallClock
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alert.AlertPlayback
import com.umamit.core.timer.alert.ControllerAlertPort
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.CoroutineTimerWakeUp
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerInputs
import com.umamit.core.timer.controller.TimerPorts
import com.umamit.core.timer.diagnostics.LogTimerDiagnostics
import com.umamit.core.timer.service.BrewTimerServicePort
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Provider

/**
 * The app of the timer's instrumented test APK (#42, D-057): the real controller with the
 * real alarm adapter, service port, alert player (#44), diagnostics log (#43), saved sheet (M1-16), database and
 * clocks, wired by hand as the app's Hilt graph will be once every port has its adapter. The ports of later
 * tickets are [LogcatTimerPorts]; the settings are the defaults ([DefaultSettings]).
 *
 * Hilt is not used here: `@HiltAndroidApp` must sit in an application module. The test
 * runner's own process creates this app too, but never builds the controller.
 */
class TimerTestApplication :
    Application(),
    TimerHost {
    val controller: BrewSessionController by lazy { buildController() }

    override val timerInputs: TimerInputs get() = controller

    private fun buildController(): BrewSessionController {
        val db = umamiDatabaseBuilder(this).build()
        val settings = DefaultSettings()
        val clocks = TimerClocks(AndroidMonotonicClock(this), SystemWallClock)
        val logcat = LogcatTimerPorts()
        val diagnostics = LogTimerDiagnostics(
            DiagnosticsLog(File(noBackupFilesDir, DiagnosticsLog.DIRECTORY), DiagnosticsLog.MAX_BYTES, Dispatchers.IO),
        )
        val ports = TimerPorts(
            alarms = AndroidTimerAlarms(this, clocks, diagnostics),
            service = BrewTimerServicePort(this, clocks, diagnostics, TimerStateNotifications(this)),
            notifier = logcat,
            alert = ControllerAlertPort(AlertPlayback(this, settings, diagnostics, clocks), Provider { controller }),
            diagnostics = diagnostics,
            savedSheet = SavedSheetRequests(AndroidMonotonicClock(this)),
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
