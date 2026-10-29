package com.umamit.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.CatalogGuideEntity
import com.umamit.core.database.entity.CatalogImageFailureEntity
import com.umamit.core.database.entity.CatalogImageFailureReason
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogSkipEntity
import com.umamit.core.database.entity.CatalogSkipReason
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogGuideEntity
import com.umamit.core.database.entity.StagedCatalogPackageEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The refresh transaction (05 §4.5): staged teas replace the catalog in one transaction, keep
 * their first sighting, and a swap that fails half-way leaves the old catalog as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogSwapTest {
    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()

    @After
    fun tearDown() = db.close()

    private fun tea(key: String, name: String, seen: Long) = CatalogTeaEntity(
        key = key,
        name = name,
        secondaryName = null,
        productUrl = "https://shop.example/t/$key",
        shopCategory = null,
        teaType = "Sencha",
        breadcrumb = "/tee/gruener-tee/japan/sencha/",
        basePriceCentsPer100g = 1000,
        available = true,
        firstSeenAt = seen,
        lastSeenAt = seen,
    )

    private fun log(at: Long, status: CatalogRefreshStatus = CatalogRefreshStatus.SUCCESS, parserVersion: Int = 1) =
        CatalogRefreshLogEntity(
            startedAt = at,
            finishedAt = at + 1,
            status = status,
            newCount = 0,
            changedCount = 0,
            discontinuedCount = 0,
            flaggedCount = 0,
            photosCount = 0,
            parserVersion = parserVersion,
            errorSummary = null,
        )

    private suspend fun swapIn(vararg teas: CatalogTeaEntity, at: Long) {
        teas.forEach { dao.stage(StagedCatalogTeaEntity(it)) }
        dao.swap(emptyList(), log(at))
    }

    private fun guide(slug: String, title: String, at: Long) = CatalogGuideEntity(
        slug = slug,
        title = title,
        url = "https://shop.example/tee-info/$slug",
        bodyMarkdown = "Ein erfundener Absatz.",
        heroFile = null,
        linkedCatalogKeys = "[]",
        fetchedAt = at,
        publishedAt = null,
    )

    private suspend fun guideTitles() = dao.observeGuides().first().map { it.title }

    private suspend fun names() = dao.observeList().first().map { it.name }

    private fun search(term: String): List<String> =
        db.openHelper.readableDatabase.query("SELECT name FROM catalog_fts WHERE catalog_fts MATCH ?", arrayOf(term))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    @Test
    fun aSwapReplacesTheCatalogAndEmptiesTheStaging() = runTest {
        swapIn(tea("XY0100", "Erster", 10), tea("XY0200", "Zweiter", 10), at = 10)

        swapIn(tea("XY0200", "Zweiter", 20), tea("XY0300", "Dritter", 20), at = 20)

        assertThat(names()).containsExactly("Dritter", "Zweiter").inOrder()
        assertThat(dao.countStaged()).isEqualTo(0)
        assertThat(dao.observeLastSuccess().first()).isEqualTo(21)
    }

    @Test
    fun aTeaSeenBeforeKeepsItsFirstSighting() = runTest {
        swapIn(tea("XY0100", "Erster", 10), at = 10)

        swapIn(tea("XY0100", "Erster", 20), tea("XY0300", "Dritter", 20), at = 20)

        val seen = db.openHelper.readableDatabase
            .query("SELECT key, first_seen_at, last_seen_at FROM catalog_tea ORDER BY key")
            .use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getLong(2))) } }
        assertThat(seen).containsExactly(Triple("XY0100", 10L, 20L), Triple("XY0300", 20L, 20L)).inOrder()
    }

    @Test
    fun theSearchIndexFollowsTheSwap() = runTest {
        swapIn(tea("XY0100", "Morgennebel", 10), at = 10)
        assertThat(search("Morgennebel")).containsExactly("Morgennebel")

        swapIn(tea("XY0200", "Abendrot", 20), at = 20)

        assertThat(search("Morgennebel")).isEmpty()
        assertThat(search("Abendrot")).containsExactly("Abendrot")
    }

    @Test
    fun skipEntriesAreAddedWithTheSwap() = runTest {
        dao.stage(StagedCatalogTeaEntity(tea("XY0100", "Erster", 10)))
        dao.swap(listOf(CatalogSkipEntity("XY0900", CatalogSkipReason.NOT_GREEN, 10)), log(10))

        assertThat(dao.skippedSince(0)).containsExactly("XY0900")
        assertThat(dao.skippedSince(11)).isEmpty()
    }

    @Test
    fun theLatestSuccessIsTheLastLoggedEvenWithTheClockSetBack() = runTest {
        // D-096: an older parser's run must not come back as the latest when the clock went back.
        dao.swap(emptyList(), log(2_000, parserVersion = 6))
        dao.swap(emptyList(), log(1_000, parserVersion = 7))
        dao.log(log(3_000, CatalogRefreshStatus.FAILED, parserVersion = 5))

        assertThat(dao.lastSuccessParserVersion()).isEqualTo(7)
        assertThat(dao.observeLastSuccessParserVersion().first()).isEqualTo(7)
    }

    @Test
    fun aTeaFromALinkLeavesTheSkipList() = runTest {
        // D-096: a product skipped not long ago and now stored from a link is read by the next refresh.
        dao.swap(
            listOf(
                CatalogSkipEntity("XY0100", CatalogSkipReason.NOT_GREEN, 10),
                CatalogSkipEntity("XY0900", CatalogSkipReason.SET, 10),
            ),
            log(10),
        )

        dao.putLinkedTea(tea("XY0100", "Erster", 20), emptyList())

        assertThat(dao.skipList().map { it.baseKey }).containsExactly("XY0900")
    }

    @Test
    fun theImagesThatFailedForGoodAreReplacedWithTheSwap() = runTest {
        // D-091: the refresh hands the swap every image URL not to ask for again; a swap without
        // them keeps those there are.
        val old =
            CatalogImageFailureEntity("https://shop.example/media/a.png", CatalogImageFailureReason.TOO_LARGE, 1, 10)
        val new =
            CatalogImageFailureEntity("https://shop.example/media/b.jpg", CatalogImageFailureReason.UNREADABLE, 1, 20)
        dao.swap(emptyList(), log(10), imageFailures = listOf(old))

        dao.swap(emptyList(), log(15))
        assertThat(dao.imageFailures()).containsExactly(old)

        dao.swap(emptyList(), log(20), imageFailures = listOf(new))
        assertThat(dao.imageFailures()).containsExactly(new)
    }

    @Test
    fun aSwapThatFailsHalfwayLeavesTheOldCatalogAsItWas() = runTest {
        swapIn(tea("XY0100", "Erster", 10), at = 10)
        // A staged package of a tea that is not staged breaks the foreign key at the very end.
        dao.stage(StagedCatalogTeaEntity(tea("XY0200", "Zweiter", 20)))
        dao.stagePackages(
            listOf(
                StagedCatalogPackageEntity(
                    CatalogPackageEntity("XY0999", "XY0999-50", "50 g", 50.0, 500, false, null, null, true),
                ),
            ),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.swap(listOf(CatalogSkipEntity("XY0900", CatalogSkipReason.NOT_GREEN, 20)), log(20)) }
        }

        assertThat(names()).containsExactly("Erster")
        assertThat(dao.skipList()).isEmpty()
        assertThat(dao.refreshLog()).hasSize(1)
        assertThat(search("Erster")).containsExactly("Erster")
    }

    @Test
    fun stagedGuidesReplaceTheGuidesWithTheSwap() = runTest {
        dao.stageGuide(StagedCatalogGuideEntity(guide("erster-guide", "Erster Guide", 10)))
        dao.stageGuide(StagedCatalogGuideEntity(guide("zweiter-guide", "Zweiter Guide", 10)))
        swapIn(tea("XY0100", "Erster", 10), at = 10)

        dao.stageGuide(StagedCatalogGuideEntity(guide("dritter-guide", "Dritter Guide", 20)))
        swapIn(tea("XY0100", "Erster", 20), at = 20)

        assertThat(guideTitles()).containsExactly("Dritter Guide")
        assertThat(dao.observeGuide("dritter-guide").first()?.fetchedAt).isEqualTo(20)
        assertThat(dao.observeGuide("erster-guide").first()).isNull()
        assertThat(dao.countStagedGuides()).isEqualTo(0)
    }

    @Test
    fun aGuideOfTheCurrentCatalogCanBeCarriedOverIntoTheStaging() = runTest {
        dao.stageGuide(StagedCatalogGuideEntity(guide("erster-guide", "Erster Guide", 10)))
        dao.stageGuide(StagedCatalogGuideEntity(guide("zweiter-guide", "Zweiter Guide", 10)))
        swapIn(tea("XY0100", "Erster", 10), at = 10)

        dao.stageCurrentGuide("zweiter-guide")
        dao.stageCurrentGuide("unbekannt")
        swapIn(tea("XY0100", "Erster", 20), at = 20)

        assertThat(guideTitles()).containsExactly("Zweiter Guide")
        assertThat(dao.observeGuide("zweiter-guide").first()?.fetchedAt).isEqualTo(10)
    }

    @Test
    fun clearingTheStagingKeepsTheGuides() = runTest {
        dao.stageGuide(StagedCatalogGuideEntity(guide("erster-guide", "Erster Guide", 10)))
        swapIn(tea("XY0100", "Erster", 10), at = 10)
        dao.stageGuide(StagedCatalogGuideEntity(guide("zweiter-guide", "Zweiter Guide", 20)))

        dao.clearStaging()

        assertThat(dao.countStagedGuides()).isEqualTo(0)
        assertThat(guideTitles()).containsExactly("Erster Guide")
    }
}
