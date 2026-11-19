package com.umamit.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.DataStoreSettingsRepository
import com.umamit.core.data.RoomRecipeRepository
import com.umamit.core.data.RoomStockRepository
import com.umamit.core.data.RoomTeaRepository
import com.umamit.core.data.RoomVesselRepository
import com.umamit.core.data.SeedData
import com.umamit.core.data.SeedDataSeeder
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.umamiDatabaseBuilder
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.SettingsSerializer
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Empty storage is a first start (SET-33, D-131 Q4, #346): after an install's data — settings with the setup done, a
 * seeded database — is gone, as `clearApplicationUserData()` leaves it, the real settings file and database open as on a
 * fresh install: the setup shows (SCR-01), the seed teas and vessels are created again and the seed photo download is
 * still to come (ONB-06). It proves only „empty storage → first start"; the wipe itself is checked on the phone (09 §4
 * „v0.9.0").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class EmptyStorageFirstStartTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    /** One install's storage under [dir]: the settings file and `umamit.db`, as the app opens them. */
    private class Install(dir: File) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val time = FakeClock(wall = 1_794_992_400_000L)
        val settings = DataStoreSettingsRepository(
            SettingsDataSource(
                DataStoreFactory.create(SettingsSerializer, scope = scope) {
                    File(dir, "files/datastore/settings.json")
                },
            ),
        )
        val db: UmamiDatabase = umamiDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            name = File(dir, "databases/umamit.db").absolutePath,
            installMarker = File(dir, "no_backup/umamit-install-marker"),
            photosDir = File(dir, "files/photos"),
        ).build()
        val teas = RoomTeaRepository(db.teaDao(), time)
        val vessels = RoomVesselRepository(db.vesselDao(), time)
        val seeder = SeedDataSeeder(
            db,
            teas,
            RoomRecipeRepository(db.recipeDao(), time),
            vessels,
            RoomStockRepository(db.inventoryDao(), time),
            settings,
            time,
        )
        val onboardingCompleted = settings.onboardingCompleted().stateIn(scope, SharingStarted.Eagerly, null)

        /** Closes the database and the DataStore, so the same files can be opened again in this process. */
        fun close() = runBlocking {
            db.close()
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun show(install: Install) = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            UmamitRoot(
                activeBrewSource = NoActiveBrewSource,
                journalSources = testJournalSources(),
                alertKey = MutableStateFlow(null),
                onboardingCompleted = install.onboardingCompleted,
                onboardingScreen = { Text("EINRICHTUNG") },
                timerScreen = { _, _, _, _ -> Text("TIMER") },
                startScreen = { Text("START") },
                settings = testSettingsScreens(),
            )
        }
    }

    @Test
    fun emptyStorageStartsAsAFirstStart() {
        val storage = folder.newFolder("data")
        // The install before: set up, seeded, its seed photos tried.
        Install(storage).run {
            runBlocking {
                assertThat(seeder.seedIfNeeded()).isTrue()
                settings.update { it.copy(onboardingCompleted = true) }
                settings.markSeedPhotosAttempted()
                assertThat(settings.settings.first().onboardingCompleted).isTrue()
            }
            close()
        }

        // All of the app's storage is gone, as after clearApplicationUserData().
        assertThat(storage.deleteRecursively()).isTrue()
        storage.mkdirs()

        val fresh = Install(storage)
        try {
            show(fresh)
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodes(hasText("EINRICHTUNG")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("START").assertDoesNotExist()

            runBlocking {
                assertThat(fresh.settings.seedDataCreated.first()).isFalse()
                assertThat(fresh.settings.seedPhotosAttempted.first()).isFalse()
                assertThat(fresh.seeder.seedIfNeeded()).isTrue()
                val teaIds = fresh.teas.observeTeas().first().map { it.tea.id }
                assertThat(teaIds).containsAtLeast(SeedData.TANABATA_TEA_ID, SeedData.HISUI_TEA_ID)
                assertThat(fresh.vessels.observeVessels().first().map { it.id }).contains(SeedData.KYUSU_VESSEL_ID)
                assertThat(fresh.settings.seedDataCreated.first()).isTrue()
            }
        } finally {
            fresh.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
