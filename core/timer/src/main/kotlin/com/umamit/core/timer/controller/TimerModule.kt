package com.umamit.core.timer.controller

import com.umamit.core.common.MonotonicClock
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alert.AlertPlayback
import com.umamit.core.timer.alert.AlertTester
import com.umamit.core.timer.alert.ControllerAlertPort
import com.umamit.core.timer.service.BrewTimerServicePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The controller's clock, in-process wake-up, alarms and service (#42, D-057),
 * alert player (#44, D-059), and *Alarm testen* for the settings (SET-31). The
 * other [TimerPorts] are bound by the tickets that bring their Android
 * adapters (diagnostics #43, notifier #47, saved sheet M1-16); until then
 * nothing can inject the controller, so no stand-in ever ships. [AlertTester]
 * does not need the controller, so the settings screen can inject it already.
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
    abstract fun alertPort(impl: ControllerAlertPort): TimerAlertPort

    @Binds
    abstract fun alertTester(impl: AlertPlayback): AlertTester
}
