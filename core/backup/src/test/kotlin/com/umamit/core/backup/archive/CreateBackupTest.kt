package com.umamit.core.backup.archive

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.SessionStart
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.StoredSettings
import com.umamit.core.datastore.settingsDataStore
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TeaCategory
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.infusion
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** *Sicherung erstellen* (BAK-01, 05 §8, #334): the file, its manifest, the snapshot, progress and cancel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreateBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java)
        .build()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings by lazy { SettingsDataSource(settingsDataStore(storeScope) { File(folder.root, "s.json") }) }
    private val photosDir by lazy { File(folder.root, "photos") }
    private val clock = FakeClock()
    private val backup by lazy {
        CreateBackup(db, settings, BackupPhotoDirectory(photosDir), AppVersion("0.9.0", 9_00_99), clock)
    }

    /** A brew of the fixture's tea with 4 g, started now, as the timer starts one. */
    private fun timerStart(id: String) = SessionStart(
        id = id,
        snapshot = SessionSnapshot(
            teaId = Ids.TEA,
            teaName = "Eigener Tee",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = null,
            vesselName = null,
            leafGrams = 4.0,
            waterMl = 200,
            baseTempC = 60,
        ),
        steps = listOf(infusion(60), infusion(20)),
        preAlertSeconds = 10,
        startedAtMs = clock.wall,
        zoneId = clock.zone.id,
    )

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    /** The entries of a ZIP: name → (bytes, method). */
    private fun entries(bytes: ByteArray): Map<String, Pair<ByteArray, Int>> {
        val out = linkedMapOf<String, Pair<ByteArray, Int>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes() to entry.method
            }
        }
        return out
    }

    private fun manifestOf(entries: Map<String, Pair<ByteArray, Int>>) =
        BackupFormat.json.decodeFromString(BackupManifest.serializer(), entries.getValue(BackupFormat.MANIFEST).text())

    private fun Pair<ByteArray, Int>.text() = first.decodeToString()

    private fun camelCase(column: String) = column.split('_').let { parts ->
        parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun columns(table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += camelCase(cursor.getString(name))
        }
        return names
    }

    /** What exporting the fixture gives: the states, the entries and the file's size. */
    private class Exported(
        val states: List<BackupExport>,
        val entries: Map<String, Pair<ByteArray, Int>>,
        val size: Int,
    )

    private suspend fun exportFixture(): Exported {
        insertFixture(db, photosDir)
        settings.update { it.copy(vibration = false, onboardingCompleted = true) }
        settings.markSeedDataCreated()
        val out = ByteArrayOutputStream()
        val states = backup.export(out).toList()
        return Exported(states, entries(out.toByteArray()), out.size())
    }

    private val photoEntries = Ids.EXPORTED_PHOTOS.map(BackupFormat::photoEntry)

    @Test
    fun `the manifest's counts and checksums match the entries`() = runBlocking {
        val exported = exportFixture()

        val entries = exported.entries
        val manifest = manifestOf(entries)
        val done = exported.states.last() as BackupExport.Done
        assertThat(entries.keys).containsExactlyElementsIn(
            photoEntries + listOf(BackupFormat.DATA, BackupFormat.SETTINGS, BackupFormat.MANIFEST),
        )
        assertThat(done.summary.manifest).isEqualTo(manifest)
        assertThat(done.summary.bytes).isEqualTo(exported.size.toLong())
        // Manifest: format, versions, time, counts and the SHA-256 of every other entry.
        assertThat(manifest.format).isEqualTo("umamit-backup")
        assertThat(manifest.formatVersion).isEqualTo(1)
        assertThat(manifest.schemaVersion).isEqualTo(9)
        assertThat(manifest.settingsSchemaVersion).isEqualTo(3)
        assertThat(manifest.appVersionName).isEqualTo("0.9.0")
        assertThat(manifest.appVersionCode).isEqualTo(9_00_99L)
        assertThat(manifest.createdAt).isEqualTo(clock.wall)
        assertThat(manifest.zoneId).isEqualTo("Europe/Berlin")
        assertThat(manifest.checksums.keys).containsExactlyElementsIn(entries.keys - BackupFormat.MANIFEST)
        manifest.checksums.forEach { (path, sum) ->
            assertWithMessage(path).that(sha256(entries.getValue(path).first)).isEqualTo(sum)
        }
        assertThat(manifest.counts).isEqualTo(
            BackupEntryCounts(
                teas = 2,
                brewProfiles = 1,
                brewSteps = 2,
                vessels = 1,
                sessions = 2,
                sessionSteps = 3,
                customDescriptors = 0,
                sessionDescriptors = 1,
                inventoryEvents = 2,
                photos = 4,
                catalogBookmarks = 1,
            ),
        )
    }

    @Test
    fun `photos are stored byte for byte and the JSON entries deflated`() = runBlocking {
        val entries = exportFixture().entries

        // Photos stored byte for byte, JSON deflated.
        photoEntries.forEach { path ->
            assertWithMessage(path).that(entries.getValue(path).second).isEqualTo(ZipEntry.STORED)
            assertThat(
                entries.getValue(path).first,
            ).isEqualTo(File(photosDir, path.removePrefix("photos/")).readBytes())
        }
        assertThat(entries.getValue(BackupFormat.DATA).second).isEqualTo(ZipEntry.DEFLATED)
    }

    @Test
    fun `data json holds every table with its columns in camelCase and reads back as the rows`() = runBlocking {
        val entries = exportFixture().entries

        // data.json: every array, each object's keys exactly the table's columns in camelCase.
        val data = BackupFormat.json.parseToJsonElement(entries.getValue(BackupFormat.DATA).text()).jsonObject
        val tables = mapOf(
            "teas" to "tea",
            "brewProfiles" to "brew_profile",
            "brewSteps" to "brew_step",
            "vessels" to "vessel",
            "sessions" to "brew_session",
            "sessionSteps" to "session_step",
            "sessionDescriptors" to "session_descriptor",
            "inventoryEvents" to "inventory_event",
            "photos" to "photo",
            "catalogBookmarks" to "catalog_bookmark",
        )
        assertThat(data.keys).containsExactlyElementsIn(tables.keys + "customDescriptors")
        assertThat(data.getValue("customDescriptors").jsonArray).isEmpty()
        tables.forEach { (array, table) ->
            val rows = data.getValue(array).jsonArray
            assertWithMessage(array).that(rows).isNotEmpty()
            rows.forEach { row -> assertWithMessage(array).that(row.jsonObject.keys).isEqualTo(columns(table)) }
        }

        // The rows read back equal the database's: ten taste columns, both ratings, the MANUAL session, photos.
        val decoded = BackupFormat.json.decodeFromString(
            BackupData.serializer(),
            entries.getValue(BackupFormat.DATA).text(),
        )
        assertThat(decoded.toRows()).isEqualTo(db.backupDao().snapshot())
        assertThat(decoded.sessions.map { it.status }).containsExactly(SessionStatus.COMPLETED, SessionStatus.MANUAL)
        assertThat(decoded.sessions.first { it.id == Ids.COMPLETED }.brewingRating).isEqualTo(3.5)
        assertThat(decoded.sessionSteps.first().tasteSpice).isEqualTo(4)
        assertThat(decoded.photos.map { it.origin }.toSet()).hasSize(2)
    }

    @Test
    fun `settings json leaves out the device-bound keys and a finished export sets lastBackupAt`() = runBlocking {
        val exported = exportFixture()
        val entries = exported.entries
        val states = exported.states

        // settings.json: all keys but the device-bound ones.
        val settingsJson = BackupFormat.json.parseToJsonElement(
            entries.getValue(BackupFormat.SETTINGS).text(),
        ).jsonObject
        assertThat(settingsJson.keys).containsNoneIn(BackupFormat.DEVICE_BOUND_SETTINGS_KEYS)
        assertThat(settingsJson.keys).containsAtLeast("schemaVersion", "vibration", "theme", "lowStockReminderInterval")
        assertThat(settingsJson.getValue("vibration").toString()).isEqualTo("false")

        // A finished export sets lastBackupAt to the snapshot's time.
        assertThat(settings.stored().lastBackupAt).isEqualTo(clock.wall)
        assertThat(states.first()).isEqualTo(BackupExport.Progress(BackupProgress.Reading))
        assertThat(states).contains(BackupExport.Progress(BackupProgress.Photos(4, 4)))
    }

    @Test
    fun `the running session, its rows and the active brew are never written`() = runBlocking {
        insertFixture(db, photosDir)
        val out = ByteArrayOutputStream()

        backup.export(out).toList()

        val entries = entries(out.toByteArray())
        val data = entries.getValue(BackupFormat.DATA).text()
        assertThat(data).doesNotContain(Ids.RUNNING)
        assertThat(data).doesNotContain("activeBrew")
        assertThat(entries.keys).doesNotContain(BackupFormat.photoEntry(Ids.PHOTO_RUNNING))
    }

    @Test
    fun `a photo whose file vanished is left out with its row and the cover that points at it`() = runBlocking {
        insertFixture(db, photosDir)
        File(photosDir, "${Ids.PHOTO_SHOP}.jpg").delete()
        val out = ByteArrayOutputStream()

        backup.export(out).toList()

        val entries = entries(out.toByteArray())
        val data = BackupFormat.json.decodeFromString(
            BackupData.serializer(),
            entries.getValue(BackupFormat.DATA).text(),
        )
        assertThat(data.photos.map { it.id }).doesNotContain(Ids.PHOTO_SHOP)
        assertThat(data.teas.first { it.id == Ids.TEA_SHOP }.coverPhotoId).isNull()
        assertThat(data.teas.first { it.id == Ids.TEA }.coverPhotoId).isEqualTo(Ids.PHOTO_OWN)
        assertThat(manifestOf(entries).counts.photos).isEqualTo(3)
        assertThat(entries.keys).doesNotContain(BackupFormat.photoEntry(Ids.PHOTO_SHOP))
    }

    @Test
    fun `a write during a running timer's step write completes and holds a consistent snapshot`() = runBlocking {
        insertFixture(db, photosDir)
        val sessions = RoomSessionRepository(db, FakeSettingsRepository(), clock)
        val timer = "session-timer"
        sessions.startSession(timerStart(timer))
        val out = ByteArrayOutputStream()
        val result = mutableListOf<BackupExport>()

        // At the first photo the rows have been read; the timer then records its step and completes its session,
        // while the export is still writing.
        backup.export(out).collect { state ->
            result += state
            if (state == BackupExport.Progress(BackupProgress.Photos(1, 4))) {
                clock.advanceBy(61_000)
                sessions.recordStep(timer, 0, StepRecord(60, 61, 0, 1, clock.wall - 61_000, clock.wall, false, false))
                clock.advanceBy(1_000)
                sessions.completeSession(timer, autoFinished = false)
            }
        }

        assertThat(result.last()).isInstanceOf(BackupExport.Done::class.java)
        val data = BackupFormat.json.decodeFromString(
            BackupData.serializer(),
            entries(out.toByteArray()).getValue(BackupFormat.DATA).text(),
        )
        // The file holds the state before the timer's writes, whole: neither the session nor its step or deduction.
        assertThat(data.sessions.map { it.id }).doesNotContain(timer)
        assertThat(data.sessionSteps.map { it.sessionId }).doesNotContain(timer)
        assertThat(data.inventoryEvents.mapNotNull { it.sessionId }).doesNotContain(timer)
        assertThat(data.counts().sessions).isEqualTo(2)
        // The timer's writes are in the database.
        assertThat(db.sessionDao().get(timer)!!.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(db.sessionDao().steps(timer).first().actualSeconds).isEqualTo(61)
    }

    @Test
    fun `a write started inside a timer's transaction waits for it and holds all of it`() = runBlocking {
        insertFixture(db, photosDir)
        val sessions = RoomSessionRepository(db, FakeSettingsRepository(), clock)
        val timer = "session-timer"
        sessions.startSession(timerStart(timer))
        val inside = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        // The timer's transition: step record and completion in one transaction (04 §7.1), held open meanwhile.
        val transition = launch(Dispatchers.IO) {
            db.withTransaction {
                sessions.recordStep(timer, 0, StepRecord(60, 61, 0, 1, clock.wall, clock.wall + 61_000, false, false))
                inside.complete(Unit)
                release.await()
                sessions.completeSession(timer, autoFinished = false)
            }
        }
        inside.await()
        val out = ByteArrayOutputStream()
        val export = async(Dispatchers.IO) { backup.export(out).toList() }
        delay(200)
        release.complete(Unit)
        transition.join()

        assertThat(export.await().last()).isInstanceOf(BackupExport.Done::class.java)
        val data = BackupFormat.json.decodeFromString(
            BackupData.serializer(),
            entries(out.toByteArray()).getValue(BackupFormat.DATA).text(),
        )
        // All of the transition, never half: the completed session with its recorded step and its deduction.
        assertThat(data.sessions.first { it.id == timer }.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(data.sessionSteps.first { it.sessionId == timer && it.position == 0 }.actualSeconds).isEqualTo(61)
        assertThat(data.inventoryEvents.mapNotNull { it.sessionId }).contains(timer)
    }

    @Test
    fun `cancel mid-photos stops the write and does not set lastBackupAt`() = runBlocking {
        insertFixture(db, photosDir)
        val out = ByteArrayOutputStream()

        backup.export(out).first { it == BackupExport.Progress(BackupProgress.Photos(1, 4)) }

        val names = entries(out.toByteArray()).keys
        assertThat(names).doesNotContain(BackupFormat.MANIFEST)
        assertThat(names).doesNotContain(BackupFormat.DATA)
        assertThat(names.size).isLessThan(Ids.EXPORTED_PHOTOS.size)
        assertThat(settings.stored().lastBackupAt).isNull()
    }

    @Test
    fun `a failed write is reported and does not set lastBackupAt`() = runBlocking {
        insertFixture(db, photosDir)
        val broken = object : OutputStream() {
            override fun write(b: Int) = throw IOException("Kein Platz")
        }

        val states = backup.export(broken).toList()

        assertThat((states.last() as BackupExport.Failed).cause).isInstanceOf(IOException::class.java)
        assertThat(settings.stored().lastBackupAt).isNull()
    }

    @Test
    fun `the safety copy's write sets no setting`() = runBlocking {
        insertFixture(db, photosDir)
        val out = ByteArrayOutputStream()

        val summary = backup.writeTo(out)

        assertThat(manifestOf(entries(out.toByteArray()))).isEqualTo(summary.manifest)
        assertThat(settings.stored().lastBackupAt).isNull()
    }

    @Test
    fun `the estimate counts sessions, teas and photos and sums the photo bytes and the JSON`() = runBlocking {
        insertFixture(db, photosDir)

        val estimate = backup.estimate()

        assertThat(estimate.sessions).isEqualTo(2)
        assertThat(estimate.teas).isEqualTo(2)
        assertThat(estimate.photos).isEqualTo(4)
        val out = ByteArrayOutputStream()
        backup.writeTo(out)
        assertThat(estimate.bytes).isAtLeast(out.size().toLong())
    }

    @Test
    fun `settings json leaves out every device-bound key`() {
        val json: JsonObject = settingsJson(StoredSettings(lastBackupAt = 5L))

        assertThat(json.keys).containsNoneIn(BackupFormat.DEVICE_BOUND_SETTINGS_KEYS)
        assertThat(json.keys).contains("lowStockReminder")
    }
}
