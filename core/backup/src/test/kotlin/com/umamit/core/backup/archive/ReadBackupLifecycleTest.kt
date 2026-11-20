package com.umamit.core.backup.archive

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.RoomRecipeRepository
import com.umamit.core.data.RoomSessionRepository
import com.umamit.core.data.RoomStockRepository
import com.umamit.core.data.RoomTeaRepository
import com.umamit.core.data.RoomVesselRepository
import com.umamit.core.data.SeedData
import com.umamit.core.data.SeedDataSeeder
import com.umamit.core.data.SessionStart
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.settingsDataStore
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.TeaCategory
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.infusion
import com.umamit.core.testing.rinse
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * The reader on what the app's own writes produce (#336 reads the safety copy with it, so a rule the app does not keep
 * would block every import), and its staging on a failure, a cancel and a misplaced file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadBackupLifecycleTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).build()
    private val clock = FakeClock()
    private val staging by lazy { File(folder.root, "nobackup/import") }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    private fun start(id: String, teaId: String) = SessionStart(
        id = id,
        snapshot = SessionSnapshot(
            teaId = teaId,
            teaName = "Bio Sencha Tanabata",
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = TeaCategory.GREEN,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = SeedData.KYUSU_VESSEL_ID,
            vesselName = "Kyusu 250 ml",
            leafGrams = 4.0,
            waterMl = 200,
            baseTempC = 60,
        ),
        steps = listOf(rinse(10), infusion(60), infusion(20), infusion(30)),
        preAlertSeconds = 10,
        startedAtMs = clock.wall,
        zoneId = clock.zone.id,
    )

    @Test
    fun `the seed data, a session through the timer's writes and fresh settings read back valid`() = runBlocking {
        val seeder = SeedDataSeeder(
            db,
            RoomTeaRepository(db.teaDao(), clock),
            RoomRecipeRepository(db.recipeDao(), clock),
            RoomVesselRepository(db.vesselDao(), clock),
            RoomStockRepository(db.inventoryDao(), clock),
            FakeSettingsRepository(),
            clock,
        )
        assertThat(seeder.seedIfNeeded()).isTrue()
        val teaId = db.backupDao().snapshot().teas.first().id
        val sessions = RoomSessionRepository(db, FakeSettingsRepository(), clock)
        sessions.startSession(start("session-1", teaId))
        val t = clock.wall
        sessions.recordStep("session-1", 0, StepRecord(10, 11, 0, 1, t, t + 11_000, false, false)) // a rinse
        sessions.recordStep("session-1", 1, StepRecord(60, 0, 0, 0, t + 11_000, t + 11_000, true, false)) // skipped
        sessions.recordStep("session-1", 2, StepRecord(20, 25, 3, 5, t + 12_000, t + 40_000, false, true))
        clock.advanceBy(60_000)
        sessions.completeSession("session-1", autoFinished = false) // the fourth step never poured
        sessions.startSession(start("session-2", teaId))
        clock.advanceBy(60_000)
        sessions.completeSession("session-2", autoFinished = true)

        val photosDir = File(folder.root, "photos").apply { mkdirs() }
        val settings = SettingsDataSource(settingsDataStore(storeScope) { File(folder.root, "settings.json") })
        val out = ByteArrayOutputStream()
        CreateBackup(db, settings, BackupPhotoDirectory(photosDir), AppVersion("0.9.0", 90_099), clock).writeTo(out)

        val result = ReadBackup().read(ByteArrayInputStream(out.toByteArray()), staging)

        assertThat(result).isInstanceOf(BackupReadResult.Valid::class.java)
        val content = (result as BackupReadResult.Valid).content
        assertThat(content.rows).isEqualTo(db.backupDao().snapshot())
        assertThat(content.rows.sessions).hasSize(2)
        assertThat(content.rows.vessels.size).isAtLeast(9)
        assertThat(content.settings).isEqualTo(settingsJson(settings.stored()))
        content.close()
    }

    @Test
    fun `a source that fails mid-copy throws and leaves no staging`() {
        val valid = zip(backupEntries(mapOf()))
        val failing = object : InputStream() {
            private var left = 10
            override fun read(): Int = if (left-- >
                0
            ) {
                valid[10 - left - 1].toInt() and 0xFF
            } else {
                throw IOException("gone")
            }
        }
        assertThrows(IOException::class.java) { runBlocking { ReadBackup().read(failing, staging) } }
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun `a cancel during the photos stops the read and leaves no staging`() = runBlocking {
        val photos = validData().photos.associate { it.id to jpeg(64, 48) }
        lateinit var cancel: () -> Unit
        val reader = ReadBackup(BackupLimits()) { cancel() }
        val bytes = zip(backupEntries(photos))
        val read =
            async(Dispatchers.IO, start = CoroutineStart.LAZY) { reader.read(ByteArrayInputStream(bytes), staging) }
        cancel = { read.cancel() }
        read.start()
        assertThrows(CancellationException::class.java) { runBlocking { read.await() } }
        assertThat(read.isCancelled).isTrue()
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun `a file inside the staging is not read`() {
        val file = File(staging, "copy.umamit").apply { parentFile!!.mkdirs() }
        file.writeBytes(zip(backupEntries(mapOf())))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ReadBackup().read(file, staging) } }
        assertThat(file.exists()).isTrue()
    }
}
