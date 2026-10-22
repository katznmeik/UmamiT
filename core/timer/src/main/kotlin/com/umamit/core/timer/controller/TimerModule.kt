package com.umamit.core.timer.controller

import com.umamit.core.common.MonotonicClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The controller's clock and in-process wake-up. The other [TimerPorts] are
 * bound by the tickets that bring their Android adapters (alarms and service
 * #42, diagnostics #43, alert player #44, notifier #47, saved sheet M1-16);
 * until then nothing can inject the controller, so no stand-in ever ships.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerModule {
    @Binds
    abstract fun monotonicClock(impl: AndroidMonotonicClock): MonotonicClock

    @Binds
    abstract fun wakeUp(impl: CoroutineTimerWakeUp): TimerWakeUpPort
}
