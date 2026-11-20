package com.umamit.feature.settings.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.umamit.core.backup.archive.AppVersion
import com.umamit.core.backup.archive.BackupPhotoDirectory
import com.umamit.core.backup.archive.CreateBackup
import com.umamit.core.backup.archive.ReadBackup
import com.umamit.core.backup.csv.ExportJournalCsv
import com.umamit.core.backup.restore.ImportPaths
import com.umamit.core.backup.restore.ImportReplacer
import com.umamit.core.backup.restore.RestoreBackup
import com.umamit.core.backup.restore.SafetyCopyExpiry
import com.umamit.core.backup.restore.UndoImport
import com.umamit.core.data.RoomActiveBrewStore
import com.umamit.core.data.RoomDatabaseTransactions
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.reminder.BackupReminderScheduler
import com.umamit.core.data.reminder.BackupReminderState
import com.umamit.core.database.ImportLock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.settingsDataStore
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSavedSheet
import com.umamit.core.testing.FakeTimerAlarms
import com.umamit.core.testing.FakeTimerAlertPlayer
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerNotifier
import com.umamit.core.testing.FakeTimerService
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * The real adapters of SCR-26 ([UseCaseDataBackup], [SystemAppDataWipe]) over an in-memory database, a settings file
 * in [root], the real backup use cases, an idle timer controller on fakes and WorkManager's test driver (synthetic,
 * D-016). [settings] is the app's settings repository, which a test may swap for a failing one ([backup]).
 */
class BackupAdapterHarness(val root: File) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val clock = FakeClock()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val db: UmamiDatabase =
        Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).allowMainThreadQueries().build()
    val stored = SettingsDataSource(settingsDataStore(scope) { File(root, "datastore/settings.json") })
    val paths = ImportPaths(File(root, "nobackup"), File(root, "files"), File(root, "databases/umamit.db"))
    val reminderState = FakeReminderState()

    val timer: BrewSessionController
    private val create: CreateBackup
    private val restore: RestoreBackup
    private val undo: UndoImport

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
        val photos = File(root, "files/photos").apply { mkdirs() }
        create = CreateBackup(db, stored, BackupPhotoDirectory(photos), AppVersion("0.9.0", 90_099), clock)
        val lock = ImportLock()
        val replacer = ImportReplacer(db, stored, paths, IdleRefresh(), clock, lock)
        val expiry = SafetyCopyExpiry(lock, paths, stored, clock)
        restore = RestoreBackup(replacer, create, ReadBackup(), expiry)
        undo = UndoImport(replacer, ReadBackup(), expiry, db)
        val timerSettings = com.umamit.core.testing.FakeSettingsRepository()
        timer = BrewSessionController(
            clocks = TimerClocks(clock, clock),
            sessions = RoomSessionRepository(db, timerSettings, clock),
            activeBrew = RoomActiveBrewStore(db, clock),
            transactions = RoomDatabaseTransactions(db),
            settings = timerSettings,
            ports = TimerPorts(
                alarms = FakeTimerAlarms(),
                service = FakeTimerService(),
                notifier = FakeTimerNotifier(),
                alert = FakeTimerAlertPlayer(),
                diagnostics = FakeTimerDiagnostics(),
                savedSheet = FakeSavedSheet(),
                wakeUp = FakeTimerWakeUp(clock),
            ),
        )
    }

    /** SCR-26's adapter with [settings] and the real reminder scheduler over them. */
    fun backup(settings: SettingsRepository): UseCaseDataBackup = UseCaseDataBackup(
        context = context,
        createBackup = create,
        readBackup = ReadBackup(),
        restoreBackup = restore,
        undoImport = undo,
        exportJournalCsv = ExportJournalCsv(db),
        paths = paths,
        settings = settings,
        stored = stored,
        db = db,
        timer = timer,
        reminders = BackupReminderScheduler(context, reminderState, settings, clock),
    )

    /** The backup reminder's unique job, as WorkManager has it. */
    fun reminderJobs(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()

    fun pendingReminder(): WorkInfo? = reminderJobs().singleOrNull { it.state == WorkInfo.State.ENQUEUED }

    fun close() = runBlocking {
        db.close()
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    class FakeReminderState : BackupReminderState {
        var lastBackup: Long? = null
        var reminderLast: Long? = null

        override suspend fun lastBackupAt(): Long? = lastBackup

        override suspend fun reminderLastAt(): Long? = reminderLast

        override suspend fun markReminderLastAt(atMs: Long) {
            reminderLast = atMs
        }

        override suspend fun sessionsSince(sinceMs: Long?): Int = 0
    }

    private class IdleRefresh : CatalogRefreshRunner {
        override val state = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)

        override fun start() = Unit

        override fun cancel() = Unit

        override fun acknowledge() = Unit

        override suspend fun recallLastResult() = Unit
    }
}
