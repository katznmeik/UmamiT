package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Bestand korrigieren* (INV-03, BRW-02, D-093 point 7; spec #81 seam 3): the new total minus the current
 * stock is one `ADJUSTMENT` event (05 §3.10), none when they are equal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockCorrectionTest {
    private val data = TestDatabase(now = 5_000L)

    @Before
    fun seed() = runTest {
        data.teas.saveTea(tea("t1"))
        // 100 g bought for 8,50 €, 4,5 g brewed: 95,5 g left.
        data.stock.addEvent(initial("e1", "t1", 100.0, 850, at = 1L))
        data.stock.addEvent(InventoryEvent("e2", "t1", InventoryEventType.BREW, -4.5, 2L, currency = null))
    }

    @After
    fun tearDown() = data.close()

    private suspend fun adjustments() =
        data.stock.observeEvents("t1").first().filter { it.type == InventoryEventType.ADJUSTMENT }

    private suspend fun remaining() = data.stock.observeStock("t1").first().remainingGrams

    @Test
    fun aLowerTotalIsOneNegativeAdjustment() = runTest {
        val written = data.stock.correctStock("t1", newTotalGrams = 23.5)

        val adjustment = adjustments().single()
        assertThat(adjustment).isEqualTo(written)
        assertThat(adjustment.deltaGrams).isWithin(1e-9).of(-72.0)
        assertThat(adjustment.occurredAtMillis).isEqualTo(5_000L)
        assertThat(adjustment.priceCents).isNull()
        assertThat(adjustment.sessionId).isNull()
        assertThat(remaining()).isWithin(1e-9).of(23.5)
    }

    @Test
    fun aHigherTotalIsOnePositiveAdjustment() = runTest {
        data.stock.correctStock("t1", newTotalGrams = 120.0)

        assertThat(adjustments().single().deltaGrams).isWithin(1e-9).of(24.5)
        assertThat(remaining()).isWithin(1e-9).of(120.0)
    }

    @Test
    fun zeroEmptiesTheTea() = runTest {
        data.stock.correctStock("t1", newTotalGrams = 0.0)

        assertThat(adjustments().single().deltaGrams).isWithin(1e-9).of(-95.5)
        assertThat(remaining()).isWithin(1e-9).of(0.0)
    }

    @Test
    fun theUnchangedTotalWritesNothing() = runTest {
        val written = data.stock.correctStock("t1", newTotalGrams = 95.5)

        assertThat(written).isNull()
        assertThat(data.stock.observeEvents("t1").first()).hasSize(2)
    }

    @Test
    fun aCorrectionNeverChangesTheCostPerGram() = runTest {
        data.stock.correctStock("t1", newTotalGrams = 10.0)

        // 850 ct / 100 g, as before the correction (05 §3.10).
        assertThat(data.stock.observeStock("t1").first().costPerGramCents!!).isWithin(1e-9).of(8.5)
    }

    @Test
    fun aTeaWithoutStockEventsGetsTheTotalAsItsAdjustment() = runTest {
        data.teas.saveTea(tea("t2"))

        data.stock.correctStock("t2", newTotalGrams = 30.0)

        assertThat(data.stock.observeStock("t2").first().remainingGrams).isWithin(1e-9).of(30.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNegativeTotalIsRefused() = runTest {
        data.stock.correctStock("t1", newTotalGrams = -1.0)
    }
}
