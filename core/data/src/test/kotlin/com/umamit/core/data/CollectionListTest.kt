package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.WallClock
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.StockLevel
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The rows of *Meine Tees* (SCR-08, TEA-08–TEA-11, D-100) on the database in memory: every tea, archived ones
 * included, with when it was added (`tea.created_at`), the start of its latest session and the Σ of its stock
 * events, and its stock level by SET-16's „Knapp ab". No schema change: the facts come from existing columns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CollectionListTest {
    private val settings = FakeSettingsRepository(UserSettings(lowStockGrams = 20.0))
    private val data = TestDatabase(settings = settings)
    private var now = 1_000L
    private val clock = object : WallClock {
        override fun nowMs(): Long = now

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val teas = RoomTeaRepository(data.db.teaDao(), clock)
    private val list = RoomCollectionListRepository(data.db.teaDao(), settings)

    @After
    fun tearDown() = data.close()

    private suspend fun rows() = list.observeRows().first().associateBy { it.row.tea.id }

    private suspend fun event(id: String, teaId: String, grams: Double) = data.stock.addEvent(
        InventoryEvent(
            id = id,
            teaId = teaId,
            type = if (grams > 0) InventoryEventType.PURCHASE else InventoryEventType.ADJUSTMENT,
            deltaGrams = grams,
            occurredAtMillis = 1L,
        ),
    )

    @Test
    fun `every tea comes with when it was added, archived ones too`() = runTest {
        now = 5_000L
        teas.saveTea(tea("a", "Asamushi"))
        now = 9_000L
        teas.saveTea(tea("b", "Bancha").copy(isArchived = true))
        // A later edit keeps the time it was added.
        now = 12_000L
        teas.saveTea(tea("a", "Asamushi Superior"))

        val rows = rows()

        assertThat(rows.keys).containsExactly("a", "b")
        assertThat(rows.getValue("a").row.addedAtMillis).isEqualTo(5_000L)
        assertThat(rows.getValue("a").row.tea.name).isEqualTo("Asamushi Superior")
        assertThat(rows.getValue("b").row.addedAtMillis).isEqualTo(9_000L)
        assertThat(rows.getValue("b").row.tea.isArchived).isTrue()
    }

    @Test
    fun `the stock is the sum of the events, also for an archived tea, and null without events`() = runTest {
        teas.saveTea(tea("a"))
        teas.saveTea(tea("b").copy(isArchived = true))
        teas.saveTea(tea("c"))
        event("e1", "a", 50.0)
        event("e2", "a", -35.0)
        event("e3", "b", 100.0)

        val rows = rows()

        assertThat(rows.getValue("a").row.remainingGrams).isEqualTo(15.0)
        // 15 g is at or below „Knapp ab" (20 g, SET-16).
        assertThat(rows.getValue("a").row.stockLevel).isEqualTo(StockLevel.LOW)
        assertThat(rows.getValue("b").row.remainingGrams).isEqualTo(100.0)
        assertThat(rows.getValue("b").row.stockLevel).isEqualTo(StockLevel.AVAILABLE)
        assertThat(rows.getValue("c").row.remainingGrams).isNull()
        assertThat(rows.getValue("c").row.stockLevel).isNull()
    }

    @Test
    fun `a change of Knapp ab changes the level`() = runTest {
        teas.saveTea(tea("a"))
        event("e1", "a", 30.0)
        assertThat(rows().getValue("a").row.stockLevel).isEqualTo(StockLevel.AVAILABLE)

        settings.update { it.copy(lowStockGrams = 40.0) }

        assertThat(rows().getValue("a").row.stockLevel).isEqualTo(StockLevel.LOW)
    }

    @Test
    fun `last brewed is the start of the tea's latest session`() = runTest {
        teas.saveTea(tea("t1"))
        teas.saveTea(tea("t2"))
        listOf("s1" to 2_000L, "s2" to 7_000L).forEach { (id, startedAt) ->
            data.sessions.startSession(
                SessionStart(id, snapshotOf("t1"), listOf(infusion(60)), 10, startedAt, "Europe/Berlin"),
            )
            data.sessions.completeSession(id, autoFinished = false)
        }

        val rows = rows()

        assertThat(rows.getValue("t1").row.lastBrewedAtMillis).isEqualTo(7_000L)
        assertThat(rows.getValue("t2").row.lastBrewedAtMillis).isNull()
    }

    @Test
    fun `a row carries the tea's cover photo`() = runTest {
        teas.saveTea(tea("t1"))
        teas.savePhoto(Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.CATALOG_COPY, 10, 10, 100))
        teas.setCoverPhoto("t1", "p1")

        assertThat(rows().getValue("t1").coverPhoto?.id).isEqualTo("p1")
    }
}
