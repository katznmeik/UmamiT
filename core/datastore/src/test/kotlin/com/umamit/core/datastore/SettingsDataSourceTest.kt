package com.umamit.core.datastore

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.AlertLength
import com.umamit.core.model.CollectionSegment
import com.umamit.core.model.CollectionSort
import com.umamit.core.model.LowStockReminderInterval
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StatsRankingBy
import com.umamit.core.model.StatsTab
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

        assertThat(settings.schemaVersion).isEqualTo(3)
        assertThat(settings.onboardingCompleted).isFalse()
        assertThat(settings.theme).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.dynamicColor).isFalse()
        assertThat(settings.alertSound).isEqualTo("klangschale")
        assertThat(settings.preAlertSound).isEqualTo("zimbel")
        assertThat(settings.alertVolumePercent).isEqualTo(100)
        assertThat(settings.ringUnderDnd).isTrue()
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
        assertThat(settings.quickTimerPreAlert).isNull()
        assertThat(settings.lowStockReminderInterval).isEqualTo(LowStockReminderInterval.DAILY)
        assertThat(settings.lowStockReminderTime).isEqualTo(18 * 60)
        assertThat(settings.lowStockReminderLastDate).isNull()
        assertThat(settings.catalogFolded).isEmpty()
        assertThat(settings.collectionSort).isEqualTo(CollectionSort.LAST_BREWED)
        assertThat(settings.collectionSegment).isEqualTo(CollectionSegment.MY_TEAS)
        assertThat(settings.statsRange).isEqualTo(StatsRange.DAYS_90)
        assertThat(settings.statsTab).isEqualTo(StatsTab.OVERVIEW)
        assertThat(settings.statsRankingBy).isEqualTo(StatsRankingBy.TEA)
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
            alertVolumePercent = 30,
            ringUnderDnd = false,
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
            quickTimerPreAlert = 20,
            lowStockReminderInterval = LowStockReminderInterval.WEEKLY,
            lowStockReminderTime = 7 * 60 + 30,
            lowStockReminderLastDate = "2026-10-30",
            catalogFolded = setOf("green/Bancha", "herbal/-"),
            collectionSort = CollectionSort.STOCK,
            collectionSegment = CollectionSegment.CATALOG,
            statsRange = StatsRange.MONTHS_12,
            statsTab = StatsTab.CONSUMPTION,
            statsFolded = setOf("overview.sessions", "ratings.ranking"),
            statsRankingBy = StatsRankingBy.BREWING,
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
    fun theQuickTimersChoiceKeepsAusApartFromAutomatischAcrossARestart() = runTest {
        // TMR-22 (v0.2.2, D-097): null is *Automatisch*, 0 is *Aus*; a file of v0.2.1 has no key and reads null.
        val v021 = """{"preAlertSeconds":20,"seedDataCreated":true}"""
        val old = File(folder.root, "quick-v021.json").apply { writeText(v021) }
        assertThat(dataSource(old).settings.first().quickTimerPreAlert).isNull()

        listOf(0, null, 30).forEach { choice ->
            val file = File(folder.root, "quick-$choice.json").apply { writeText(v021) }
            val writerScope = CoroutineScope(coroutineContext + Job())
            val writer = SettingsDataSource(settingsDataStore(writerScope) { file })
            writer.update { it.copy(quickTimerPreAlert = choice) }
            writerScope.cancel()
            writerScope.coroutineContext.job.join()

            val reader = dataSource(file)
            assertThat(reader.settings.first().quickTimerPreAlert).isEqualTo(choice)
            assertThat(reader.settings.first().preAlertSeconds).isEqualTo(20)
            assertThat(reader.seedDataCreated.first()).isTrue()
        }
    }

    @Test
    fun aFileOfVersion022WithoutALautstaerkeReadsHundredPercentWithoutAMigration() = runTest {
        // SET-27 is new in v0.3.0 (D-098): a missing key reads as its default, no step of its own (05 §6).
        val file = File(folder.root, "v022.json")
        file.writeText("""{"schemaVersion":2,"alertSound":"glocke","lowStockGrams":20.0,"seedDataCreated":true}""")
        val source = dataSource(file)

        val settings = source.settings.first()

        assertThat(settings.alertVolumePercent).isEqualTo(100)
        // SET-28 and SET-29 (INV-11) too: the reminder is on (daily) at 18:00 after the update.
        assertThat(settings.lowStockReminderInterval).isEqualTo(LowStockReminderInterval.DAILY)
        assertThat(settings.lowStockReminderTime).isEqualTo(1080)
        assertThat(settings.schemaVersion).isEqualTo(3)
        assertThat(settings.alertSound).isEqualTo("glocke")
        assertThat(settings.lowStockGrams).isEqualTo(20.0)
        assertThat(source.seedDataCreated.first()).isTrue()
    }

    @Test
    fun aFileOfVersion030Rc1WithoutBeiNichtStoerenKlingelnReadsOnWithoutAMigration() = runTest {
        // SET-32 is new in v0.3.0-rc.2 (D-099): a missing key reads as its default, on; no step of its own.
        val file = File(folder.root, "v030rc1.json")
        file.writeText("""{"schemaVersion":2,"alertVolumePercent":40,"vibration":false,"seedDataCreated":true}""")
        val source = dataSource(file)

        val settings = source.settings.first()

        assertThat(settings.ringUnderDnd).isTrue()
        assertThat(settings.schemaVersion).isEqualTo(3)
        assertThat(settings.alertVolumePercent).isEqualTo(40)
        assertThat(settings.vibration).isFalse()
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
        assertThat(migrated.schemaVersion).isEqualTo(3)
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
        assertThat(settings.schemaVersion).isEqualTo(3)
    }

    @Test
    fun unreadableFileReadsTheDefaults() = runTest {
        val file = File(folder.root, "broken.json")
        file.writeText("not json")

        assertThat(dataSource(file).settings.first()).isEqualTo(UserSettings())
    }

    @Test
    fun aFileOfVersion022WithoutFoldedGroupsReadsNoneFoldedWithoutAMigration() = runTest {
        // `catalogFolded` is new in v0.3.0 (CAT-10, D-098): a missing key reads ∅, no step of its own (05 §6).
        val file = File(folder.root, "v022-folded.json")
        file.writeText("""{"schemaVersion":2,"alertSound":"glocke","seedDataCreated":true}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.catalogFolded).isEmpty()
        assertThat(settings.schemaVersion).isEqualTo(3)
        assertThat(settings.alertSound).isEqualTo("glocke")
    }

    @Test
    fun eachCollectionSortAndSegmentIsStoredAndReadBack() = runTest {
        // TEA-11, SCR-08 (v0.4.0, D-100): `collectionSort` and `collectionSegment` round-trip (05 §6).
        val source = dataSource()
        for (sort in CollectionSort.entries) {
            for (segment in CollectionSegment.entries) {
                source.update { it.copy(collectionSort = sort, collectionSegment = segment) }

                val settings = source.settings.first()
                assertThat(settings.collectionSort).isEqualTo(sort)
                assertThat(settings.collectionSegment).isEqualTo(segment)
            }
        }
    }

    @Test
    fun aFileOfVersion030WithoutTheCollectionKeysReadsTheDefaultsWithoutAMigration() = runTest {
        // The v0.4.0 keys (D-100): a missing key reads its default, no step of its own (05 §6).
        val file = File(folder.root, "v030-collection.json")
        file.writeText("""{"schemaVersion":2,"catalogFolded":["green/Bancha"],"seedDataCreated":true}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.collectionSort).isEqualTo(CollectionSort.LAST_BREWED)
        assertThat(settings.collectionSegment).isEqualTo(CollectionSegment.MY_TEAS)
        assertThat(settings.schemaVersion).isEqualTo(3)
        assertThat(settings.catalogFolded).containsExactly("green/Bancha")
    }

    @Test
    fun unknownCollectionSortOrSegmentNamesReadTheDefaults() = runTest {
        val file = File(folder.root, "odd-collection.json")
        file.writeText("""{"collectionSort":"STARS","collectionSegment":"TEAWARE","alertSound":"glocke"}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.collectionSort).isEqualTo(CollectionSort.LAST_BREWED)
        assertThat(settings.collectionSegment).isEqualTo(CollectionSegment.MY_TEAS)
        assertThat(settings.alertSound).isEqualTo("glocke")
    }

    @Test
    fun eachStatsRangeAndTabIsStoredAndReadBack() = runTest {
        // INS-07 (v0.7.0, D-115): `statsRange` and `statsTab` round-trip (05 §6).
        val source = dataSource()
        for (range in StatsRange.entries) {
            for (tab in StatsTab.entries) {
                source.update { it.copy(statsRange = range, statsTab = tab) }

                val settings = source.settings.first()
                assertThat(settings.statsRange).isEqualTo(range)
                assertThat(settings.statsTab).isEqualTo(tab)
            }
        }
    }

    @Test
    fun theRankingSwitchIsStoredAndReadBackAndAnUnknownNameReadsTea() = runTest {
        // INS-02 (v0.7.1, D-117): `statsRankingBy` TEA/BREWING, default TEA, an unknown name reads TEA (05 §6).
        val source = dataSource()
        for (by in StatsRankingBy.entries) {
            source.update { it.copy(statsRankingBy = by) }

            assertThat(source.settings.first().statsRankingBy).isEqualTo(by)
        }
        val odd = File(folder.root, "odd-ranking.json").apply { writeText("""{"statsRankingBy":"TASTE"}""") }
        assertThat(dataSource(odd).settings.first().statsRankingBy).isEqualTo(StatsRankingBy.TEA)
    }

    @Test
    fun theCustomStatsMonthsAreStoredAsYearMonthAndReadBack() = runTest {
        // INS-07 (D-115 Amendment 1): `statsRangeFrom` / `statsRangeTo` as `yyyy-MM`, null until chosen (05 §6).
        val file = File(folder.root, "custom-range.json")
        val source = dataSource(file)
        assertThat(source.settings.first().statsRangeFrom).isNull()

        source.update {
            it.copy(
                statsRange = StatsRange.CUSTOM,
                statsRangeFrom = java.time.YearMonth.of(2026, 3),
                statsRangeTo = java.time.YearMonth.of(2026, 9),
            )
        }

        val settings = source.settings.first()
        assertThat(settings.statsRange).isEqualTo(StatsRange.CUSTOM)
        assertThat(settings.statsRangeFrom).isEqualTo(java.time.YearMonth.of(2026, 3))
        assertThat(settings.statsRangeTo).isEqualTo(java.time.YearMonth.of(2026, 9))
        assertThat(file.readText()).contains("\"2026-03\"")
    }

    @Test
    fun aFileOfVersion060WithoutTheStatsKeysReadsTheDefaultsAndACorruptValueDoes() = runTest {
        val old = File(folder.root, "v060-stats.json")
        old.writeText("""{"collectionSort":"NAME","alertSound":"glocke"}""")
        val odd = File(folder.root, "odd-stats.json")
        odd.writeText("""{"statsRange":"WEEKS_2","statsTab":"","alertSound":"glocke"}""")

        for (file in listOf(old, odd)) {
            val settings = dataSource(file).settings.first()

            assertThat(settings.statsRange).isEqualTo(StatsRange.DAYS_90)
            assertThat(settings.statsTab).isEqualTo(StatsTab.OVERVIEW)
            assertThat(settings.alertSound).isEqualTo("glocke")
        }
    }

    @Test
    fun theV050StepTurnsTheReminderSwitchIntoItsIntervalOnce() = runTest {
        // SET-28 (D-108 point 7, #198): a stored „on" reads as *Täglich*, „off" as *Aus*; the rest stays.
        for ((stored, interval) in listOf(
            true to LowStockReminderInterval.DAILY,
            false to LowStockReminderInterval.OFF,
        )) {
            val file = File(folder.root, "v041-$stored.json")
            file.writeText(
                """{"schemaVersion":2,"lowStockReminder":$stored,"lowStockReminderTime":1290,""" +
                    """"lowStockReminderLastDate":"2026-11-08","seedDataCreated":true}""",
            )
            val firstStart = CoroutineScope(coroutineContext + Job())
            val source = SettingsDataSource(settingsDataStore(firstStart) { file })

            val migrated = source.settings.first()
            assertThat(migrated.schemaVersion).isEqualTo(3)
            assertThat(migrated.lowStockReminderInterval).isEqualTo(interval)
            assertThat(migrated.lowStockReminderTime).isEqualTo(1290)
            assertThat(migrated.lowStockReminderLastDate).isEqualTo("2026-11-08")
            assertThat(source.seedDataCreated.first()).isTrue()

            // The PO then picks *Alle 3 Tage*: the next start keeps it, the step never runs again.
            source.update { it.copy(lowStockReminderInterval = LowStockReminderInterval.EVERY_3_DAYS) }
            firstStart.cancel()
            firstStart.coroutineContext.job.join()

            assertThat(dataSource(file).settings.first().lowStockReminderInterval)
                .isEqualTo(LowStockReminderInterval.EVERY_3_DAYS)
        }
    }

    @Test
    fun anOlderFileWithoutTheReminderKeyReadsDaily() = runTest {
        // A file of v0.2.x had no SET-28 key: it read „on", so it reads *Täglich*.
        val file = File(folder.root, "v021-reminder.json")
        file.writeText("""{"schemaVersion":1,"lowStockGrams":10.0}""")

        val settings = dataSource(file).settings.first()

        assertThat(settings.lowStockReminderInterval).isEqualTo(LowStockReminderInterval.DAILY)
        assertThat(settings.schemaVersion).isEqualTo(3)
    }

    @Test
    fun eachReminderIntervalIsStoredAndReadBackAndAnUnknownNameReadsDaily() = runTest {
        val source = dataSource()
        for (interval in LowStockReminderInterval.entries) {
            source.update { it.copy(lowStockReminderInterval = interval) }

            assertThat(source.settings.first().lowStockReminderInterval).isEqualTo(interval)
        }
        val file = File(folder.root, "odd-interval.json")
        file.writeText("""{"schemaVersion":3,"lowStockReminderInterval":"HOURLY","lowStockReminder":false}""")

        assertThat(dataSource(file).settings.first().lowStockReminderInterval)
            .isEqualTo(LowStockReminderInterval.DAILY)
    }

    @Test
    fun backupReminderIsOnByDefaultIsStoredAndAnOlderFileWithoutTheKeyReadsOn() = runTest {
        // SET-20 (05 §6, D-130 Q11): *An Sicherung erinnern*, on by default; a file of v0.8.x has no key, no migration.
        val source = dataSource()
        assertThat(source.settings.first().backupReminderEnabled).isTrue()

        source.update { it.copy(backupReminderEnabled = false) }

        assertThat(source.settings.first().backupReminderEnabled).isFalse()
        assertThat(source.stored().backupReminderEnabled).isFalse()
        val file = File(folder.root, "v080.json")
        file.writeText("""{"schemaVersion":3,"vibration":false}""")
        val older = dataSource(file).settings.first()
        assertThat(older.backupReminderEnabled).isTrue()
        assertThat(older.schemaVersion).isEqualTo(3)
    }

    @Test
    fun lastBackupAtIsNullUntilAnExportAndSurvivesASettingsUpdate() = runTest {
        // BAK-01 (05 §6): set by a finished export only; an update of the user's settings keeps it.
        val source = dataSource()
        assertThat(source.lastBackupAt.first()).isNull()

        source.markLastBackupAt(1_234L)
        source.update { it.copy(vibration = false) }

        assertThat(source.lastBackupAt.first()).isEqualTo(1_234L)
        assertThat(source.stored().lastBackupAt).isEqualTo(1_234L)
        assertThat(source.stored().vibration).isFalse()
    }

    @Test
    fun safetyCopyAtIsSetAndClearedAndSurvivesASettingsUpdate() = runTest {
        // BAK-02 (05 §6): only the import writes it; an update of the user's settings keeps it.
        val source = dataSource()
        assertThat(source.safetyCopyAt.first()).isNull()

        source.setSafetyCopyAt(5_678L)
        source.update { it.copy(vibration = false) }
        assertThat(source.safetyCopyAt.first()).isEqualTo(5_678L)

        source.setSafetyCopyAt(null)
        assertThat(source.stored().safetyCopyAt).isNull()
    }

    @Test
    fun updateStoredWritesTheStoredKeysInOneWrite() = runTest {
        // 05 §8 „Replace" step 4: the import lays a backup's keys over the stored ones; the rest stays.
        val source = dataSource()
        source.markLastBackupAt(9L)

        source.updateStored { it.copy(theme = "DARK", lowStockGrams = 30.0) }

        val stored = source.stored()
        assertThat(stored.theme).isEqualTo("DARK")
        assertThat(stored.lowStockGrams).isEqualTo(30.0)
        assertThat(stored.lastBackupAt).isEqualTo(9L)
    }
}
