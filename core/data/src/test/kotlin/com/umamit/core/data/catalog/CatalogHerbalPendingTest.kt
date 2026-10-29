package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
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
 * Whether the Kräutertees are still to come (v0.2.1, #91; D-093 Q20, SCR-19): true while the last
 * successful refresh ran a parser from before the Kräutertees, so the first refresh after the
 * update hasn't run yet; false before any refresh (the empty catalog has its own state) and after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogHerbalPendingTest {
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

    private suspend fun pending() = repository.observeHerbalPending().first()

    private val before = TeeKontorParser.HERBAL_SINCE_VERSION - 1

    @Test
    fun beforeAnyRefreshNothingIsPending() = runTest {
        assertThat(pending()).isFalse()
    }

    @Test
    fun aCatalogFromBeforeTheUpdateWaitsForTheKraeutertees() = runTest {
        refreshLog(before)

        assertThat(pending()).isTrue()
    }

    @Test
    fun aStoppedRefreshAfterTheUpdateKeepsThemPending() = runTest {
        refreshLog(before)
        refreshLog(TeeKontorParser.HERBAL_SINCE_VERSION, CatalogRefreshStatus.FAILED)

        assertThat(pending()).isTrue()
    }

    @Test
    fun theFirstSuccessfulRefreshAfterTheUpdateBringsThem() = runTest {
        refreshLog(before)
        refreshLog(TeeKontorParser.HERBAL_SINCE_VERSION)

        assertThat(pending()).isFalse()
    }
}
