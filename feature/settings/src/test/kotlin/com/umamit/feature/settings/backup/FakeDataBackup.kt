package com.umamit.feature.settings.backup

import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.backup.archive.BackupPreview
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.backup.restore.UndoPreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** A [DataBackup] on synthetic data: each call is recorded, each answer set by the test. */
class FakeDataBackup : DataBackup {
    override val lastBackup = MutableStateFlow<LastBackup?>(null)
    override val reminderEnabled = MutableStateFlow(true)
    override val timerActive = MutableStateFlow(false)

    var estimate: BackupEstimate? = BackupEstimate(sessions = 142, teas = 23, photos = 61, bytes = 18L * 1024 * 1024)

    /** What the running export emits; the test sends into it and closes it. */
    var exportEvents = Channel<BackupExport>(Channel.UNLIMITED)
    val exported = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var exportCancelled = false

    var checkResult: BackupCheck = BackupCheck.Refused(0)
    val checked = mutableListOf<String>()

    var undoPreview: UndoPreview? = null
    var undoResult: RestoreResult = RestoreResult.Done(PREVIEW, finishedAtNextStart = false)
    var undoGate: CompletableDeferred<Unit>? = null
    var undoCalls = 0

    var journalOk = true
    val journals = mutableListOf<String>()

    var reminderWriteOk = true

    override suspend fun setReminderEnabled(on: Boolean): Boolean {
        if (reminderWriteOk) reminderEnabled.value = on
        return reminderWriteOk
    }

    override suspend fun estimate(): BackupEstimate? = estimate

    override fun export(document: String): Flow<BackupExport> {
        exported += document
        return exportEvents.receiveAsFlow()
    }

    override suspend fun delete(document: String) {
        deleted += document
    }

    override suspend fun check(document: String): BackupCheck {
        checked += document
        return checkResult
    }

    override suspend fun undoPreview(): UndoPreview? = undoPreview

    override suspend fun undo(): RestoreResult {
        undoCalls++
        undoGate?.await()
        return undoResult
    }

    override suspend fun exportJournal(document: String): Boolean {
        journals += document
        return journalOk
    }

    companion object {
        /** 3 Nov 2026, 10:00 in Berlin. */
        val PREVIEW = BackupPreview(
            createdAt = 1_793_696_400_000L,
            zoneId = "Europe/Berlin",
            sessions = 142,
            teas = 23,
            photos = 61,
        )
    }
}

/** A [StagedBackup] that records its close and answers [result]. */
class FakeStagedBackup(var result: RestoreResult = RestoreResult.Done(FakeDataBackup.PREVIEW, false)) : StagedBackup {
    var closed = 0
    var restores = 0

    override suspend fun restore(): RestoreResult {
        restores++
        return result
    }

    override fun close() {
        closed++
    }
}
