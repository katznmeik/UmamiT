package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The daily low-stock reminder's teas (INV-11, D-098; ticket #130) on the collection in memory: every
 * non-archived tea with stock events that is *Knapp* or *Leer* by the Start card's rules (INV-05, INV-10; a
 * tea's own threshold first, inclusive), hidden ones included, least stock first. Synthetic teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowStockReminderQueryTest {
    private val settings = FakeSettingsRepository()
    private val data = TestDatabase(settings = settings)
    private val lowStock = RoomLowStockRepository(data.db.inventoryDao(), settings, FakeCatalogRepository())

    @After
    fun tearDown() = data.close()

    private suspend fun teaWith(
        id: String,
        grams: Double,
        name: String = "Tee $id",
        archived: Boolean = false,
        ownThreshold: Double? = null,
    ) {
        data.teas.saveTea(tea(id, name).copy(isArchived = archived, lowStockGrams = ownThreshold))
        data.stock.addEvent(initial("$id-buy", id, 50.0, 900, at = 1L))
        data.stock.addEvent(
            InventoryEvent("$id-used", id, InventoryEventType.ADJUSTMENT, grams - 50.0, 2L, currency = null),
        )
    }

    private suspend fun names(): List<String> = lowStock.reminderTeas().map { it.teaName }

    @Test
    fun theThresholdIsInclusive() = runTest {
        teaWith("t1", grams = 15.0, name = "Tanabata")
        teaWith("t2", grams = 15.1, name = "Hisui")

        val teas = lowStock.reminderTeas()

        assertThat(teas.map { it.teaName }).containsExactly("Tanabata")
        assertThat(teas.single().teaId).isEqualTo("t1")
        assertThat(teas.single().remainingGrams).isWithin(1e-9).of(15.0)
    }

    @Test
    fun everyLowTeaIsListedWithTheLeastStockFirst() = runTest {
        teaWith("t1", grams = 14.0, name = "Tanabata")
        teaWith("t2", grams = 0.0, name = "Hisui")
        teaWith("t3", grams = 40.0, name = "Asamushi")
        teaWith("t4", grams = 3.0, name = "Kabuse")

        assertThat(names()).containsExactly("Hisui", "Kabuse", "Tanabata").inOrder()
        assertThat(lowStock.reminderTeas().first().isEmpty).isTrue()
    }

    @Test
    fun archivedTeasAndTeasWithoutStockEventsAreNeverListed() = runTest {
        teaWith("t1", grams = 2.0, name = "Entfernt", archived = true)
        data.teas.saveTea(tea("t2", "Ohne Vorrat"))

        assertThat(lowStock.reminderTeas()).isEmpty()
    }

    @Test
    fun teasHiddenOnStartAreStillListed() = runTest {
        teaWith("t1", grams = 3.0, name = "Tanabata")
        teaWith("t2", grams = 10.0, name = "Hisui")
        settings.update { it.copy(lowStockHidden = setOf("t1/t1-buy", "t2/t2-buy")) }

        assertThat(names()).containsExactly("Tanabata", "Hisui").inOrder()
    }

    @Test
    fun aTeasOwnThresholdWinsOverTheSetting() = runTest {
        teaWith("t1", grams = 25.0, name = "Eigene Schwelle hoch", ownThreshold = 30.0)
        teaWith("t2", grams = 10.0, name = "Eigene Schwelle niedrig", ownThreshold = 5.0)

        assertThat(names()).containsExactly("Eigene Schwelle hoch")
    }
}
