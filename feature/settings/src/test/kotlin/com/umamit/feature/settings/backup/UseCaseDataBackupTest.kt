package com.umamit.feature.settings.backup

import android.net.Uri
import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * SCR-26's adapter over the real use cases and the real reminder scheduler (#338, #339; D-133 item 13): SET-20 on
 * and off enqueue and cancel the job, a finished export replaces it before `Done` is emitted, a failed one does not,
 * and a failing write answers false instead of ending the app. The session count is not checked here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UseCaseDataBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val harness by lazy { BackupAdapterHarness(folder.newFolder("app")) }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `switching the reminder on saves it and schedules the job from now`() = runBlocking {
        val settings = FakeSettingsRepository(UserSettings(backupReminderEnabled = false))
        val backup = harness.backup(settings)

        assertThat(backup.setReminderEnabled(true)).isTrue()

        assertThat(settings.settings.first().backupReminderEnabled).isTrue()
        assertThat(harness.reminderState.reminderLast).isEqualTo(harness.clock.nowMs())
        assertThat(harness.pendingReminder()).isNotNull()
    }

    @Test
    fun `switching the reminder off saves it and cancels the job`() = runBlocking {
        val settings = FakeSettingsRepository()
        val backup = harness.backup(settings)
        backup.setReminderEnabled(true)

        assertThat(backup.setReminderEnabled(false)).isTrue()

        assertThat(settings.settings.first().backupReminderEnabled).isFalse()
        assertThat(harness.pendingReminder()).isNull()
        assertThat(harness.reminderJobs().map { it.state }).contains(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `a failing save answers false and schedules nothing`() = runBlocking {
        val settings = FakeSettingsRepository(UserSettings(backupReminderEnabled = false)).apply { failUpdates = true }
        val backup = harness.backup(settings)

        assertThat(backup.setReminderEnabled(true)).isFalse()

        assertThat(settings.settings.first().backupReminderEnabled).isFalse()
        assertThat(harness.pendingReminder()).isNull()
    }

    @Test
    fun `a runtime failure of the switch answers false instead of ending the app`() = runBlocking {
        val failing = object : SettingsRepository by FakeSettingsRepository() {
            override suspend fun update(transform: (UserSettings) -> UserSettings) =
                throw IllegalStateException("synthetic WorkManager or store failure")
        }
        val backup = harness.backup(failing)

        assertThat(backup.setReminderEnabled(false)).isFalse()
        assertThat(backup.setReminderEnabled(true)).isFalse()
    }

    @Test
    fun `a finished export replaces the job before Done`() = runBlocking {
        val backup = harness.backup(FakeSettingsRepository())
        val file = File(folder.root, "UmamiT-Sicherung.umamit")
        var pendingAtDone: WorkInfo? = null

        val events = mutableListOf<BackupExport>()
        backup.export(Uri.fromFile(file).toString()).collect { event ->
            if (event is BackupExport.Done) pendingAtDone = harness.pendingReminder()
            events += event
        }

        assertThat(events.last()).isInstanceOf(BackupExport.Done::class.java)
        assertThat(pendingAtDone).isNotNull()
        assertThat(file.length()).isGreaterThan(0L)
    }

    @Test
    fun `an export with the reminder off schedules nothing`() = runBlocking {
        val backup = harness.backup(FakeSettingsRepository(UserSettings(backupReminderEnabled = false)))
        val file = File(folder.root, "UmamiT-Sicherung.umamit")

        val events = backup.export(Uri.fromFile(file).toString()).toList()

        assertThat(events.last()).isInstanceOf(BackupExport.Done::class.java)
        assertThat(harness.pendingReminder()).isNull()
    }

    @Test
    fun `a document that cannot be opened fails and schedules nothing`() = runBlocking {
        val backup = harness.backup(FakeSettingsRepository())

        val events = backup.export("content://com.umamit.nowhere/document/1").toList()

        assertThat(events.single()).isInstanceOf(BackupExport.Failed::class.java)
        assertThat(harness.pendingReminder()).isNull()
    }
}
