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
import java.time.LocalDate

/** The stock history's delete (INV-03, SCR-29, D-104): purchases and corrections go, brews stay (D-102). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockHistoryDeleteTest {
    private val data = TestDatabase()
    private val opened = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1").copy(openedDate = opened))
        // 100 g for 8,00 € and 50 g for 6,00 €: 14,00 € / 150 g.
        data.stock.addEvent(initial("e1", "t1", 100.0, 800, at = 10L))
        data.stock.addEvent(InventoryEvent("p1", "t1", InventoryEventType.PURCHASE, 50.0, 20L, priceCents = 600))
        data.stock.addEvent(InventoryEvent("b1", "t1", InventoryEventType.BREW, -5.0, 30L, sessionId = null))
        data.stock.addEvent(InventoryEvent("a1", "t1", InventoryEventType.ADJUSTMENT, -15.0, 40L))
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun `deleting a purchase takes its grams and price out of the totals and keeps the opened date`() = runTest {
        assertThat(data.stock.deleteEvent("p1")).isTrue()

        val stock = data.stock.observeStock("t1").first()
        assertThat(stock.remainingGrams).isWithin(1e-9).of(80.0)
        assertThat(stock.costPerGramCents!!).isWithin(1e-9).of(8.0)
        assertThat(data.stock.observeEvents("t1").first().map { it.id }).containsExactly("e1", "b1", "a1").inOrder()
        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(opened)
    }

    @Test
    fun `deleting a correction gives its grams back and leaves the price`() = runTest {
        assertThat(data.stock.deleteEvent("a1")).isTrue()

        val stock = data.stock.observeStock("t1").first()
        assertThat(stock.remainingGrams).isWithin(1e-9).of(145.0)
        assertThat(stock.costPerGramCents!!).isWithin(1e-9).of(1400.0 / 150.0)
        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(opened)
    }

    @Test
    fun `a brew is refused and nothing changes`() = runTest {
        assertThat(data.stock.deleteEvent("b1")).isFalse()

        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(130.0)
        assertThat(data.stock.observeEvents("t1").first().map { it.id }).contains("b1")
    }

    @Test
    fun `the first package is a purchase too and can be deleted`() = runTest {
        assertThat(data.stock.deleteEvent("e1")).isTrue()

        val stock = data.stock.observeStock("t1").first()
        assertThat(stock.remainingGrams).isWithin(1e-9).of(30.0)
        assertThat(stock.costPerGramCents!!).isWithin(1e-9).of(12.0)
        assertThat(data.teas.getTea("t1")!!.openedDate).isEqualTo(opened)
    }

    @Test
    fun `an unknown event is refused`() = runTest {
        assertThat(data.stock.deleteEvent("nope")).isFalse()
    }
}
