package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.model.CatalogKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which catalog kinds are still to come (v0.2.1 #91, v0.5.0 #203, #205; D-093 Q20, D-108, SCR-19): those
 * whose section a parser after the last successful refresh's brought, so the first refresh after
 * the update hasn't run yet; none before any refresh (the empty catalog has its own state) and after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogPendingKindsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val repository by lazy { RoomCatalogRepository(dao, CatalogPhotoFiles(folder.newFolder())) }

    @After
    fun tearDown() = db.close()

    private var at = 0L

    private suspend fun refreshLog(parserVersion: Int, status: CatalogRefreshStatus = CatalogRefreshStatus.SUCCESS) {
        at += 1_000
        dao.log(
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
            ),
        )
    }

    private suspend fun pending() = repository.observePendingKinds().first()

    @Test
    fun beforeAnyRefreshNothingIsPending() = runTest {
        assertThat(pending()).isEmpty()
    }

    @Test
    fun aCatalogOfV041WaitsForTheBlackTeasAndTheSectionsOfV9AndV10() = runTest {
        refreshLog(PARSER_V041)

        assertThat(pending()).containsExactly(
            CatalogKind.BLACK,
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
            CatalogKind.ROOIBOS,
            CatalogKind.FRUIT,
            CatalogKind.CHAI,
        )
    }

    @Test
    fun aCatalogAnRcRefreshedWithParserV8WaitsForTheWhiteTeasOolongsAndPuErhs() = runTest {
        refreshLog(PARSER_V8)

        assertThat(pending()).containsExactly(
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
            CatalogKind.ROOIBOS,
            CatalogKind.FRUIT,
            CatalogKind.CHAI,
        )
    }

    @Test
    fun aCatalogAnRcRefreshedWithParserV9WaitsForTheRotbuschFruechteteeAndChai() = runTest {
        refreshLog(PARSER_V9)

        assertThat(pending()).containsExactly(CatalogKind.ROOIBOS, CatalogKind.FRUIT, CatalogKind.CHAI)
    }

    @Test
    fun aCatalogOfV020WaitsForTheKraeuterteesAndTheBlackTeas() = runTest {
        refreshLog(PARSER_V020)

        assertThat(
            pending(),
        ).containsExactly(
            CatalogKind.HERBAL,
            CatalogKind.BLACK,
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
            CatalogKind.ROOIBOS,
            CatalogKind.FRUIT,
            CatalogKind.CHAI,
        )
    }

    @Test
    fun aStoppedRefreshAfterTheUpdateKeepsThemPending() = runTest {
        refreshLog(PARSER_V041)
        refreshLog(TeeKontorParser.PARSER_VERSION, CatalogRefreshStatus.FAILED)

        assertThat(pending()).containsExactly(
            CatalogKind.BLACK,
            CatalogKind.WHITE,
            CatalogKind.OOLONG,
            CatalogKind.DARK,
            CatalogKind.ROOIBOS,
            CatalogKind.FRUIT,
            CatalogKind.CHAI,
        )
    }

    @Test
    fun theFirstSuccessfulRefreshAfterTheUpdateBringsThem() = runTest {
        refreshLog(PARSER_V020)
        refreshLog(TeeKontorParser.PARSER_VERSION)

        assertThat(pending()).isEmpty()
    }

    private companion object {
        /** v0.2.0's parser: neither Kräutertees nor black teas. */
        const val PARSER_V020 = 6

        /** v0.4.1's parser: Kräutertees, no black teas. */
        const val PARSER_V041 = 7

        /** The black teas' parser (#203), which an rc of v0.5.0 may have refreshed with. */
        const val PARSER_V8 = 8

        /** The white teas', oolongs' and Pu-Erhs' parser (#205), which an rc of v0.5.0 may have refreshed with. */
        const val PARSER_V9 = 9
    }
}
