package com.umamit.app

import android.app.Application
import android.os.StrictMode
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerInputs
import com.umamit.core.timer.diagnostics.AppStartDiagnostics
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * The app. It owns the timer: the alarm receiver and the notification actions reach
 * the [BrewSessionController] through it ([TimerHost], D-057), since the system
 * creates them. The process start recovers the persisted timer (04 §7.2, D-063).
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

    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // The permission states of this process start, for *Diagnose* (06 §5.6); binder calls,
        // so off the main thread, and never in the way of an alarm that started the process.
        thread(name = "UmamiT-app-start-diagnostics") { appStartDiagnostics.record() }
        // The process start recovers the timer (04 §7.2, D-063), off the main thread, so the
        // screens show a step that ran before a kill or a force stop at once. A process an
        // alarm started may recover in that alarm's turn instead; recovery runs once either way.
        recoveryScope.launch { controller.get().recover(RecoveryTrigger.APP_START) }
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
