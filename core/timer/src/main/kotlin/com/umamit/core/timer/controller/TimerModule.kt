package com.umamit.core.timer.controller

import com.umamit.core.common.MonotonicClock
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alert.AlertPlayback
import com.umamit.core.timer.alert.AlertTester
import com.umamit.core.timer.alert.ControllerAlertPort
import com.umamit.core.timer.diagnostics.AndroidPermissionStatesReader
import com.umamit.core.timer.diagnostics.LogTimerDiagnostics
import com.umamit.core.timer.diagnostics.PermissionStatesReader
import com.umamit.core.timer.notification.AndroidTimerNotifier
import com.umamit.core.timer.service.BrewTimerServicePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The controller's clock, in-process wake-up, alarms and service (#42, D-057),
 * alert player (#44, D-059), diagnostics log and permission states (#43,
 * D-060), the saved sheet (M1-16, D-061), the notifier (#47, D-064), and
 * *Alarm testen* for the settings (SET-31): every port of [TimerPorts], so the
 * controller can be injected (D-056). [AlertTester] does not need the
 * controller.
 *
 * The alarm receiver reaches the controller through the app
 * ([TimerHost], D-057).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerModule {
    @Binds
    abstract fun monotonicClock(impl: AndroidMonotonicClock): MonotonicClock

    @Binds
    abstract fun wakeUp(impl: CoroutineTimerWakeUp): TimerWakeUpPort

    @Binds
    abstract fun alarms(impl: AndroidTimerAlarms): TimerAlarmPort

    @Binds
    abstract fun service(impl: BrewTimerServicePort): TimerServicePort

    @Binds
    abstract fun notifier(impl: AndroidTimerNotifier): TimerNotifierPort

    @Binds
    abstract fun alertPort(impl: ControllerAlertPort): TimerAlertPort

    @Binds
    abstract fun savedSheet(impl: SavedSheetRequests): SavedSheetPort

    @Binds
    abstract fun alertTester(impl: AlertPlayback): AlertTester

    @Binds
    abstract fun diagnostics(impl: LogTimerDiagnostics): TimerDiagnosticsPort

    @Binds
    abstract fun permissionStates(impl: AndroidPermissionStatesReader): PermissionStatesReader
}
