package com.umamit.core.datastore

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.ThemeMode
import com.umamit.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Settings (05 §6) and the seed marker in DataStore. */
class SettingsDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataSource(file: File = File(folder.root, "settings.json")) =
        SettingsDataSource(settingsDataStore(backgroundScope) { file })

    @Test
    fun freshInstallReadsTheSpecDefaults() = runTest {
        val source = dataSource()
        val settings = source.settings.first()

        assertThat(settings.schemaVersion).isEqualTo(2)
        assertThat(settings.onboardingCompleted).isFalse()
        assertThat(settings.theme).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.dynamicColor).isFalse()
        assertThat(settings.alertSound).isEqualTo("klangschale")
        assertThat(settings.preAlertSound).isEqualTo("zimbel")
        assertThat(settings.preAlertSeconds).isEqualTo(10)
        assertThat(settings.vibration).isTrue()
        assertThat(settings.alertLength).isEqualTo(AlertLength.NORMAL)
        assertThat(settings.deductStock).isTrue()
        assertThat(settings.lowStockGrams).isEqualTo(15.0)
        assertThat(settings.lowStockHidden).isEmpty()
        assertThat(settings.keepScreenOn).isTrue()
        assertThat(settings.dimWhileSteeping).isTrue()
        assertThat(settings.dismissedHints).isEmpty()
        assertThat(settings.catalogDownloadPhotos).isTrue()
        assertThat(source.seedDataCreated.first()).isFalse()
        assertThat(source.seedPhotosAttempted.first()).isFalse()
        assertThat(source.seedVesselsV2Added.first()).isFalse()
    }

    @Test
    fun theSeedTeaLinkPartsAddUpAndSurviveSettingsChanges() = runTest {
        // CAT-15 links each part of each seed tea once (#70, D-090): a settings change must never undo one.
        val source = dataSource()
        assertThat(source.seedTeaLinksDone.first()).isEmpty()

        source.markSeedTeaLinksDone(setOf("tanabata/description"))
        source.update { it.copy(catalogDownloadPhotos = false) }
        source.markSeedTeaLinksDone(setOf("tanabata/cover", "hisui/description"))

        assertThat(source.seedTeaLinksDone.first())
            .containsExactly("tanabata/description", "tanabata/cover", "hisui/description")
        assertThat(source.seedDataCreated.first()).isFalse()
    }

    @Test
    fun changesArePersistedAndReadBack() = runTest {
        val file = File(folder.root, "persisted.json")
        val changed = UserSettings(
            onboardingCompleted = true,
            theme = ThemeMode.DARK,
            dynamicColor = true,
            alertSound = "glocke",
            preAlertSound = "klangstab",
            preAlertSeconds = 0,
            vibration = false,
            alertLength = AlertLength.LONG,
            deductStock = false,
            lowStockGrams = 25.0,
            lowStockHidden = setOf("tanabata/e1"),
            keepScreenOn = false,
            dimWhileSteeping = false,
            dismissedHints = setOf("swipe", "stock"),
            catalogDownloadPhotos = false,
        )
        // Write with one DataStore, close it, and read the file back with a new one (an app restart).
        val writerScope = CoroutineScope(coroutineContext + Job())
        val writer = SettingsDataSource(settingsDataStore(writerScope) { file })
        writer.update { changed }
        writer.markSeedDataCreated()
        writer.markSeedPhotosAttempted()
        writer.markSeedVesselsV2Added()
        writerScope.cancel()
        writerScope.coroutineContext.job.join()

        val reader = dataSource(file)
        assertThat(reader.settings.first()).isEqualTo(changed)
        assertThat(reader.seedDataCreated.first()).isTrue()
        assertThat(reader.seedPhotosAttempted.first()).isTrue()
        assertThat(reader.seedVesselsV2Added.first()).isTrue()
    }

    @Test
    fun corruptOrUnknownValuesFallBackToDefaults() = runTest {
        val file = File(folder.root, "odd.json")
        file.writeText("""{"theme":"PURPLE","alertLength":"HUGE","preAlertSeconds":15,"futureKey":1}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.theme).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.alertLength).isEqualTo(AlertLength.NORMAL)
        assertThat(settings.preAlertSeconds).isEqualTo(15)
    }

    @Test
    fun aFileOfVersion020WithoutAPreAlertSoundReadsTheZimbelAndKeepsTheRest() = runTest {
        // SET-26 is new in v0.2.1: an update keeps every other setting (05 §6).
        val file = File(folder.root, "v020.json")
        file.writeText("""{"alertSound":"sanft","preAlertSeconds":20,"seedDataCreated":true}""")
        val source = dataSource(file)

        val settings = source.settings.first()

        assertThat(settings.preAlertSound).isEqualTo("zimbel")
        assertThat(settings.alertSound).isEqualTo("sanft")
        assertThat(settings.preAlertSeconds).isEqualTo(20)
        assertThat(source.seedDataCreated.first()).isTrue()
    }

    @Test
    fun theOldDefaultOf10GramsBecomes15Once() = runTest {
        // SET-16 had no settings row before v0.2.2, so a stored 10,0 g is the old default (D-097).
        val file = File(folder.root, "v021.json")
        file.writeText("""{"schemaVersion":1,"lowStockGrams":10.0,"preAlertSeconds":20,"seedDataCreated":true}""")
        val firstStart = CoroutineScope(coroutineContext + Job())
        val source = SettingsDataSource(settingsDataStore(firstStart) { file })

        val migrated = source.settings.first()
        assertThat(migrated.lowStockGrams).isEqualTo(15.0)
        assertThat(migrated.schemaVersion).isEqualTo(2)
        assertThat(migrated.preAlertSeconds).isEqualTo(20)
        assertThat(source.seedDataCreated.first()).isTrue()

        // The PO then picks 10 g on purpose: the next start keeps it.
        source.update { it.copy(lowStockGrams = 10.0) }
        firstStart.cancel()
        firstStart.coroutineContext.job.join()

        assertThat(dataSource(file).settings.first().lowStockGrams).isEqualTo(10.0)
    }

    @Test
    fun anyOtherStoredThresholdStays() = runTest {
        val file = File(folder.root, "v021-20.json")
        file.writeText("""{"schemaVersion":1,"lowStockGrams":20.0}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.lowStockGrams).isEqualTo(20.0)
        assertThat(settings.schemaVersion).isEqualTo(2)
    }

    @Test
    fun unreadableFileReadsTheDefaults() = runTest {
        val file = File(folder.root, "broken.json")
        file.writeText("not json")

        assertThat(dataSource(file).settings.first()).isEqualTo(UserSettings())
    }
}
