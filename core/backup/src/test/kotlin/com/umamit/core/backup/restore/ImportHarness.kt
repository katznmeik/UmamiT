package com.umamit.core.backup.restore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.backup.archive.AppVersion
import com.umamit.core.backup.archive.BackupContent
import com.umamit.core.backup.archive.BackupFormat
import com.umamit.core.backup.archive.BackupPhotoDirectory
import com.umamit.core.backup.archive.BackupReadResult
import com.umamit.core.backup.archive.CreateBackup
import com.umamit.core.backup.archive.Ids
import com.umamit.core.backup.archive.ReadBackup
import com.umamit.core.backup.archive.T0
import com.umamit.core.backup.archive.backupEntries
import com.umamit.core.backup.archive.dataTree
import com.umamit.core.backup.archive.insertFixture
import com.umamit.core.backup.archive.settingsJson
import com.umamit.core.backup.archive.settingsTree
import com.umamit.core.backup.archive.toBackup
import com.umamit.core.backup.archive.validRows
import com.umamit.core.backup.archive.validStoredSettings
import com.umamit.core.backup.archive.zip
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.database.INSTALL_MARKER_FILE
import com.umamit.core.database.ImportLock
import com.umamit.core.database.PHOTOS_DIR
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.BackupRows
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.StoredSettings
import com.umamit.core.datastore.settingsDataStore
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

// The replacing import's world for the tests (synthetic, D-016): a file-backed umamit.db opened as the app opens it
// (restore detection included), the real settings DataStore, photos/ and the no-backup folder in a temporary root.
// [restart] is a new process: everything closed, the start gate run, then everything opened again.

/** A failure injected at a checkpoint: caught by the import like any I/O error. */
class InjectedFailure : java.io.IOException("injected")

/** A crash at a checkpoint: nothing of the import's own handling runs after it, as when the process dies. */
class SimulatedCrash : Error("simulated crash")

/** What the user has: the personal rows, the photo files' bytes and the settings that a backup carries. */
data class World(val rows: BackupRows, val photos: Map<String, String>, val settings: JsonObject)

class FakeRefresh : CatalogRefreshRunner {
    override val state = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)

    fun running() {
        state.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
    }

    override fun start() = Unit

    override fun cancel() = Unit

    override fun acknowledge() = Unit

    override suspend fun recallLastResult() = Unit
}

val NO_TIMER = TimerActivity { false }

class ImportHarness(val root: File) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val noBackup = File(root, "nobackup")
    val files = File(root, "files")
    val dbFile = File(root, "databases/umamit.db")
    val installMarker = File(noBackup, INSTALL_MARKER_FILE)
    val photosDir = File(files, PHOTOS_DIR)
    private val settingsFile = File(root, "datastore/settings.json")
    val paths = ImportPaths(noBackup, files, dbFile)
    val clock = FakeClock()
    val refresh = FakeRefresh()

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    lateinit var db: UmamiDatabase
        private set
    lateinit var settings: SettingsDataSource
        private set
    lateinit var lock: ImportLock
        private set
    lateinit var replacer: ImportReplacer
        private set
    lateinit var expiry: SafetyCopyExpiry
        private set
    lateinit var restore: RestoreBackup
        private set
    lateinit var undo: UndoImport
        private set

    init {
        dbFile.parentFile!!.mkdirs()
        noBackup.mkdirs()
        open()
    }

    private fun open() {
        db = umamiDatabaseBuilder(context, dbFile.path, installMarker, photosDir).build()
        settings = SettingsDataSource(settingsDataStore(scope) { settingsFile })
        lock = ImportLock()
        replacer = ImportReplacer(db, settings, paths, refresh, clock, lock)
        expiry = SafetyCopyExpiry(lock, paths, settings, clock)
        val create = CreateBackup(db, settings, BackupPhotoDirectory(photosDir), AppVersion("0.9.0", 90_099), clock)
        restore = RestoreBackup(replacer, create, ReadBackup(), expiry)
        undo = UndoImport(replacer, ReadBackup(), expiry, db)
    }

    /** A new process: closed, then the start gate (before Room opens), then the background housekeeping. */
    fun restart(gate: Boolean = true) = runBlocking {
        db.close()
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        open()
        if (gate) ImportRecovery(lock, paths, settings, clock).recoverAtStart()
        expiry.atAppStart()
    }

    fun close() = runBlocking {
        db.close()
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    /** The old data: the fixture (its running session removed), real JPEGs, settings away from their defaults. */
    fun seedOld() = runBlocking {
        insertFixture(db, photosDir)
        db.openHelper.writableDatabase.execSQL("DELETE FROM brew_session WHERE id = '${Ids.RUNNING}'")
        File(photosDir, "${Ids.PHOTO_RUNNING}.jpg").delete()
        Ids.EXPORTED_PHOTOS.forEach { File(photosDir, "$it.jpg").writeBytes(jpeg(32, 24, Color.GREEN)) }
        settings.updateStored {
            it.copy(theme = "LIGHT", vibration = false, onboardingCompleted = true, lastBackupAt = 1L)
        }
    }

    fun world(): World = runBlocking {
        World(
            rows = db.backupDao().snapshot(),
            photos = photosDir.listFiles().orEmpty().associate { it.name to it.readBytes().toHex() },
            settings = settingsJson(settings.stored()),
        )
    }

    fun stored(): StoredSettings = runBlocking { settings.stored() }

    /** The new file's content, staged as #338 stages a picked file. */
    fun newContent(rows: BackupRows = newRows(), color: Int = Color.BLUE): BackupContent = runBlocking {
        val photos = rows.photos.associate { it.id to jpeg(64, 48, color) }
        val bytes = zip(backupEntries(photos, dataTree(rows.toBackup()), settingsTree(newSettings())))
        val result = ReadBackup().read(ByteArrayInputStream(bytes), paths.staging)
        (result as BackupReadResult.Valid).content
    }

    /** The world [newContent] gives: its rows and photos, its settings over the device-bound keys kept. */
    fun newWorld(rows: BackupRows = newRows(), color: Int = Color.BLUE) = World(
        rows = rows,
        photos = rows.photos.associate { "${it.id}${BackupFormat.PHOTO_SUFFIX}" to jpeg(64, 48, color).toHex() },
        settings = settingsTree(newSettings()),
    )

    fun activeBrew(slot: TimerSlot) = runBlocking { db.activeBrewDao().upsert(activeBrewRow(slot)) }

    fun hasActiveBrew(): Boolean = runBlocking { TimerSlot.entries.any { db.activeBrewDao().get(it) != null } }

    /** The files a finished or rolled back replace leaves none of. */
    fun leftovers(): List<File> = listOf(
        paths.marker,
        paths.settings,
        paths.photosNew,
        paths.photosOld,
        paths.safetyNew,
        paths.undoStaging,
        paths.check,
    ).filter { it.exists() }
}

/** Different from the old data in every tea's name and every photo's bytes, so a mix shows. */
fun newRows(name: String = "Neu"): BackupRows {
    val rows = validRows()
    // In the snapshot's order (each list by its primary key), so the rows compare as read back.
    return BackupRows(
        teas = rows.teas.map { it.copy(name = "$name ${it.name}") }.sortedBy { it.id },
        brewProfiles = rows.brewProfiles.sortedBy { it.id },
        brewSteps = rows.brewSteps.sortedBy { it.id },
        vessels = rows.vessels.sortedBy { it.id },
        sessions = rows.sessions.sortedBy { it.id },
        sessionSteps = rows.sessionSteps.sortedBy { it.id },
        sessionDescriptors = rows.sessionDescriptors.sortedBy { "${it.sessionId}/${it.descriptorKey}" },
        inventoryEvents = rows.inventoryEvents.sortedBy { it.id },
        photos = rows.photos.sortedBy { it.id },
        catalogBookmarks = rows.catalogBookmarks.sortedBy { it.catalogKey },
    )
}

fun newSettings() = validStoredSettings()

private val jpegs = HashMap<Triple<Int, Int, Int>, ByteArray>()

/** A real JPEG (the reader decodes every photo), the same bytes for the same arguments. */
fun jpeg(width: Int, height: Int, color: Int): ByteArray = synchronized(jpegs) {
    jpegs.getOrPut(Triple(width, height, color)) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun activeBrewRow(slot: TimerSlot) = ActiveBrewEntity(
    slot = slot,
    sessionId = if (slot == TimerSlot.QUICK) null else Ids.COMPLETED,
    isQuickTimer = slot == TimerSlot.QUICK,
    quickDurationSeconds = if (slot == TimerSlot.QUICK) 120 else null,
    stepIndex = 0,
    phase = TimerPhase.RUNNING,
    deadlineElapsedMs = 60_000,
    deadlineWallMs = T0 + 60_000,
    preAlertElapsedMs = null,
    preAlertPlayed = false,
    remainingMs = null,
    pausedTotalMs = 0,
    pausedSinceElapsedMs = null,
    pausedSinceWallMs = null,
    readySinceWallMs = null,
    stepStartedWallMs = T0,
    alertPlayed = false,
    bootCount = 1,
    lastInteractionWallMs = T0,
    demoted = false,
    preAlertInheritedSeconds = 10,
    zoneId = "Europe/Berlin",
    createdAt = T0,
    updatedAt = T0,
    quickPreAlertSeconds = null,
    alertRingingSinceElapsedMs = null,
    alertRingingSinceWallMs = null,
)
