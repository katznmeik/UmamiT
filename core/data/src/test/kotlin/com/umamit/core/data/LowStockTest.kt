package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.LowStockCard
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Start's low-stock card (INV-05, INV-10, SET-16, D-097; spec #108 seam 4) on the collection in memory:
 * the non-archived tea with stock events at or below its threshold and the least stock left, not hidden,
 * with INV-09's reorder link. Teas and shop pages are synthetic (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowStockTest {
    private val settings = FakeSettingsRepository()
    private val catalog = FakeCatalogRepository()
    private val data = TestDatabase(settings = settings)
    private val lowStock = RoomLowStockRepository(data.db.inventoryDao(), settings, catalog)

    @After
    fun tearDown() = data.close()

    private suspend fun teaWith(
        id: String,
        grams: Double,
        name: String = "Tee $id",
        catalogKey: String? = null,
        archived: Boolean = false,
        ownThreshold: Double? = null,
    ) {
        data.teas.saveTea(
            tea(id, name).copy(
                catalogKey = catalogKey,
                styleKey = "sencha",
                isArchived = archived,
                lowStockGrams = ownThreshold,
            ),
        )
        data.stock.addEvent(initial("$id-buy", id, 50.0, 900, at = 1L))
        data.stock.addEvent(brew("$id-used", id, grams - 50.0, at = 2L))
    }

    private fun brew(id: String, teaId: String, grams: Double, at: Long) =
        InventoryEvent(id, teaId, InventoryEventType.ADJUSTMENT, grams, at, currency = null)

    private suspend fun card(): LowStockCard? = lowStock.observeCard().first()

    @Test
    fun theThresholdIsInclusive() = runTest {
        teaWith("t1", grams = 15.0, name = "Tanabata")

        val card = card()

        assertThat(card?.teaId).isEqualTo("t1")
        assertThat(card?.teaName).isEqualTo("Tanabata")
        assertThat(card?.styleKey).isEqualTo("sencha")
        assertThat(card?.remainingGrams).isWithin(1e-9).of(15.0)
        assertThat(card?.isEmpty).isFalse()
    }

    @Test
    fun aTeaAboveTheThresholdShowsNoCard() = runTest {
        teaWith("t1", grams = 15.1)

        assertThat(card()).isNull()
    }

    @Test
    fun theTeaWithTheLeastStockLeftTakesTheSlot() = runTest {
        teaWith("t1", grams = 14.0)
        teaWith("t2", grams = 3.0)
        teaWith("t3", grams = 40.0)

        assertThat(card()?.teaId).isEqualTo("t2")
    }

    @Test
    fun anEmptyTeaCountsToo() = runTest {
        teaWith("t1", grams = 12.0)
        teaWith("t2", grams = 0.0)

        val card = card()

        assertThat(card?.teaId).isEqualTo("t2")
        assertThat(card?.isEmpty).isTrue()
    }

    @Test
    fun archivedTeasAndTeasWithoutStockEventsNeverShow() = runTest {
        teaWith("t1", grams = 2.0, archived = true)
        data.teas.saveTea(tea("t2"))

        assertThat(card()).isNull()
    }

    @Test
    fun aTeasOwnThresholdWinsOverTheSetting() = runTest {
        teaWith("t1", grams = 25.0, ownThreshold = 30.0)
        teaWith("t2", grams = 10.0, ownThreshold = 5.0)

        assertThat(card()?.teaId).isEqualTo("t1")
    }

    @Test
    fun aHigherSettingMakesATeaKnapp() = runTest {
        teaWith("t1", grams = 18.0)
        assertThat(card()).isNull()

        settings.update { it.copy(lowStockGrams = 20.0) }

        assertThat(card()?.teaId).isEqualTo("t1")
    }

    @Test
    fun hidingSkipsTheTeaUntilItsNextPurchase() = runTest {
        teaWith("t1", grams = 3.0)
        teaWith("t2", grams = 10.0)

        lowStock.hide(card()!!)
        assertThat(card()?.teaId).isEqualTo("t2")
        lowStock.hide(card()!!)
        assertThat(card()).isNull()

        // A newer purchase shows the card again while the stock is still at or below the threshold.
        data.stock.addEvent(initial("t1-buy2", "t1", 10.0, 300, at = 3L))
        val back = card()
        assertThat(back?.teaId).isEqualTo("t1")
        assertThat(back?.remainingGrams).isWithin(1e-9).of(13.0)
    }

    @Test
    fun hidingAgainReplacesTheTeasEarlierEntry() = runTest {
        teaWith("t1", grams = 3.0)
        lowStock.hide(card()!!)
        data.stock.addEvent(initial("t1-buy2", "t1", 10.0, 300, at = 3L))

        lowStock.hide(card()!!)

        assertThat(settings.settings.first().lowStockHidden).containsExactly("t1/t1-buy2")
    }

    @Test
    fun reorderOpensThePageOfTheSizeBoughtLast() = runTest {
        teaWith("t1", grams = 14.0, catalogKey = "TX0001")
        catalog.details.value = mapOf("TX0001" to shopTea("TX0001"))

        assertThat(card()?.reorderUrl).isEqualTo("https://example.invalid/tx0001-50")
    }

    @Test
    fun aDiscontinuedTeaOrOneWithoutCatalogLinkHasNoReorder() = runTest {
        teaWith("t1", grams = 14.0, catalogKey = "TX0001")
        catalog.details.value = mapOf("TX0001" to shopTea("TX0001").copy(discontinued = true))
        assertThat(card()?.reorderUrl).isNull()

        data.teas.saveTea(data.teas.getTea("t1")!!.copy(catalogKey = null))
        catalog.details.value = mapOf("TX0001" to shopTea("TX0001"))
        assertThat(card()?.teaId).isEqualTo("t1")
        assertThat(card()?.reorderUrl).isNull()
    }

    @Test
    fun aCatalogKeyWithoutALoadedCatalogFallsBackToTheTeasProductPage() = runTest {
        teaWith("t1", grams = 14.0, catalogKey = "TX0001")
        data.teas.saveTea(data.teas.getTea("t1")!!.copy(productUrl = "https://example.invalid/tx0001"))

        assertThat(card()?.reorderUrl).isEqualTo("https://example.invalid/tx0001")
    }

    @Test
    fun theDefaultThresholdIs15Grams() {
        assertThat(UserSettings().lowStockGrams).isEqualTo(15.0)
    }

    private fun shopTea(key: String) = CatalogTeaDetail(
        key = key,
        name = "Synthetischer Sencha",
        productUrl = "https://example.invalid/${key.lowercase()}",
        packages = listOf(50.0, 100.0).map { grams ->
            CatalogPackage(
                articleNumber = "$key-${grams.toInt()}",
                label = "${grams.toInt()} g",
                grams = grams,
                priceCents = 900,
                priceIsEstimate = false,
                savingsPercent = null,
                variantUrl = "https://example.invalid/${key.lowercase()}-${grams.toInt()}",
                available = true,
            )
        },
    )
}
