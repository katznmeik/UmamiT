package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

fun initial(id: String, teaId: String, grams: Double, priceCents: Long?, at: Long = 1L) = InventoryEvent(
    id = id,
    teaId = teaId,
    type = InventoryEventType.INITIAL,
    deltaGrams = grams,
    occurredAtMillis = at,
    priceCents = priceCents,
    packageLabel = "100 g",
)

/** Stock (05 §3.10) and vessels (05 §3.4) through the repositories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockAndVesselRepositoryTest {
    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    @Test
    fun stockEventsAreReadBackOldestFirst() = runTest {
        data.teas.saveTea(tea("t1"))
        val later = initial("e2", "t1", 50.0, 500, at = 20L).copy(type = InventoryEventType.PURCHASE)
        val first = initial("e1", "t1", 100.0, 850, at = 10L)
        data.stock.addEvent(later)
        data.stock.addEvent(first)

        assertThat(data.stock.observeEvents("t1").first()).containsExactly(first, later).inOrder()
    }

    @Test
    fun remainingStockAndCostPerGramForAMixOfAllEventTypes() = runTest {
        data.teas.saveTea(tea("t1"))
        data.stock.addEvent(initial("e1", "t1", 100.0, 850, at = 1L))
        data.stock.addEvent(
            InventoryEvent("e2", "t1", InventoryEventType.PURCHASE, 50.0, 2L, priceCents = 650),
        )
        data.stock.addEvent(InventoryEvent("e3", "t1", InventoryEventType.BREW, -4.5, 3L, priceCents = null))
        data.stock.addEvent(InventoryEvent("e4", "t1", InventoryEventType.ADJUSTMENT, -10.0, 4L))
        // A purchase without a price does not change the cost per gram.
        data.stock.addEvent(InventoryEvent("e5", "t1", InventoryEventType.PURCHASE, 20.0, 5L, priceCents = null))

        val stock = data.stock.observeStock("t1").first()

        assertThat(stock.remainingGrams).isWithin(1e-9).of(155.5)
        // (850 + 650) ct / (100 + 50) g = 10 ct/g
        assertThat(stock.costPerGramCents!!).isWithin(1e-9).of(10.0)
    }

    @Test
    fun teaWithoutPricedEventsHasNoCostPerGram() = runTest {
        data.teas.saveTea(tea("t1"))
        data.stock.addEvent(initial("e1", "t1", 30.0, priceCents = null))

        val stock = data.stock.observeStock("t1").first()

        assertThat(stock.remainingGrams).isWithin(1e-9).of(30.0)
        assertThat(stock.costPerGramCents).isNull()
    }

    @Test
    fun deletedEventNoLongerCounts() = runTest {
        data.teas.saveTea(tea("t1"))
        data.stock.addEvent(initial("e1", "t1", 100.0, 850))
        data.stock.addEvent(InventoryEvent("e2", "t1", InventoryEventType.ADJUSTMENT, -20.0, 2L))

        data.stock.deleteEvent("e2")

        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(100.0)
    }

    @Test
    fun vesselsAreStoredUpdatedListedByNameAndDeleted() = runTest {
        val kyusu = Vessel("v1", "Kyusu", VesselType.KYUSU, 300, 250, VesselMaterial.UNGLAZED_CLAY)
        val mug = Vessel("v2", "Becher mit Sieb", VesselType.MUG_INFUSER, 400)
        data.vessels.saveVessel(kyusu)
        data.vessels.saveVessel(mug)
        data.vessels.saveVessel(kyusu.copy(isFavorite = true))

        assertThat(data.vessels.observeVessels().first())
            .containsExactly(mug, kyusu.copy(isFavorite = true)).inOrder()

        data.vessels.deleteVessel("v2")
        assertThat(data.vessels.getVessel("v2")).isNull()
    }

    @Test
    fun deletingAVesselKeepsTheRecipeWithoutVessel() = runTest {
        data.teas.saveTea(tea("t1"))
        data.vessels.saveVessel(Vessel("v1", "Kyusu", VesselType.KYUSU, 300))
        data.recipes.saveRecipe(recipe("r1", "t1").copy(vesselId = "v1"))

        data.vessels.deleteVessel("v1")

        assertThat(data.recipes.getRecipe("r1")!!.vesselId).isNull()
    }
}
