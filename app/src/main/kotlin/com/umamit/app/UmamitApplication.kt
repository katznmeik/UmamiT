package com.umamit.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.umamit.core.data.FirstStart
import com.umamit.core.data.catalog.CatalogRefreshHost
import com.umamit.core.data.catalog.CatalogRefreshServicePort
import com.umamit.core.data.reminder.LowStockReminderScheduler
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
 * creates them. The catalog refresh's service reaches the refresh the same way, and
 * separately ([CatalogRefreshHost], #65). The process start recovers the persisted
 * timer (04 §7.2, D-063) and retries a failed seed (D-075).
 *
 * WorkManager starts on demand with Hilt's worker factory ([Configuration.Provider]; its default
 * initializer is removed in the manifest). The process start follows the daily low-stock reminder's
 * settings (INV-11, D-098), which also re-arms it after an app update.
 */
@HiltAndroidApp
class UmamitApplication :
    Application(),
    TimerHost,
    CatalogRefreshHost,
    Configuration.Provider {
    @Inject
    lateinit var appStartDiagnostics: AppStartDiagnostics

    @Inject
    lateinit var controller: Lazy<BrewSessionController>

    @Inject
    lateinit var firstStart: Lazy<FirstStart>

    @Inject
    lateinit var catalogRefreshPort: Lazy<CatalogRefreshServicePort>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var lowStockReminders: Lazy<LowStockReminderScheduler>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override val timerInputs: TimerInputs get() = controller.get()

    override val catalogRefresh: CatalogRefreshServicePort get() = catalogRefreshPort.get()

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
        // A first start whose seed failed gets the seed teas now (D-075), off the main thread
        // (the graph is built there too); the seed photo download is never sent from here.
        recoveryScope.launch { firstStart.get().retrySeedAtAppStart() }
        // The daily low-stock reminder (INV-11): keeps a waiting job, follows SET-28 and SET-29 from now on.
        recoveryScope.launch { lowStockReminders.get().follow() }
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
