package com.umamit.app

import android.app.Application
import android.os.StrictMode
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerInputs
import com.umamit.core.timer.diagnostics.AppStartDiagnostics
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * The app. It owns the timer: the alarm receiver and the notification actions reach
 * the [BrewSessionController] through it ([TimerHost], D-057), since the system
 * creates them. The controller is built on first use, so an app
 * start without a timer event does not touch the database for it.
 */
@HiltAndroidApp
class UmamitApplication :
    Application(),
    TimerHost {
    @Inject
    lateinit var appStartDiagnostics: AppStartDiagnostics

    @Inject
    lateinit var controller: Lazy<BrewSessionController>

    override val timerInputs: TimerInputs get() = controller.get()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // The permission states of this process start, for *Diagnose* (06 §5.6); binder calls,
        // so off the main thread, and never in the way of an alarm that started the process.
        thread(name = "UmamiT-app-start-diagnostics") { appStartDiagnostics.record() }
    }

    /** Debug builds only (06 §6): surface disk/network access on the main thread and leaks early. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }
}
