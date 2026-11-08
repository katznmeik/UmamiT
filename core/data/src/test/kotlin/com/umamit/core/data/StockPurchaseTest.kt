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
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * *Nachkauf erfassen* (INV-03, SCR-09 VORRAT, D-104; spec #171 seam 1): one `PURCHASE` event of the grams,
 * with the price when one was given, on the chosen day (05 §3.10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockPurchaseTest {
    /** 8 Nov 2026, 15:00 UTC (the test clock's zone). */
    private val now = LocalDate.of(2026, 11, 8).atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val data = TestDatabase(now = now)

    @Before
    fun seed() = runTest {
        data.teas.saveTea(tea("t1"))
        // 100 g bought for 8,50 €, 4,5 g brewed: 95,5 g left.
        data.stock.addEvent(initial("e1", "t1", 100.0, 850, at = 1L))
        data.stock.addEvent(InventoryEvent("e2", "t1", InventoryEventType.BREW, -4.5, 2L, currency = null))
    }

    @After
    fun tearDown() = data.close()

    private suspend fun purchases() =
        data.stock.observeEvents("t1").first().filter { it.type == InventoryEventType.PURCHASE }

    private suspend fun summary() = data.stock.observeStock("t1").first()

    @Test
    fun aPricedPurchaseTodayIsOnePurchaseEventAtNow() = runTest {
        val written = data.stock.recordPurchase("t1", TeaPurchase(50.0, 600, LocalDate.of(2026, 11, 8)))

        val purchase = purchases().single()
        assertThat(purchase).isEqualTo(written)
        assertThat(purchase.deltaGrams).isWithin(1e-9).of(50.0)
        assertThat(purchase.priceCents).isEqualTo(600)
        assertThat(purchase.occurredAtMillis).isEqualTo(now)
        assertThat(purchase.sessionId).isNull()
        assertThat(purchase.packageLabel).isEqualTo("50 g")
    }

    @Test
    fun theTotalsAfterAPricedPurchase() = runTest {
        data.stock.recordPurchase("t1", TeaPurchase(50.0, 600, LocalDate.of(2026, 11, 8)))

        assertThat(summary().remainingGrams).isWithin(1e-9).of(145.5)
        // (850 + 600) ct / (100 + 50) g.
        assertThat(summary().costPerGramCents!!).isWithin(1e-9).of(1450.0 / 150.0)
    }

    @Test
    fun aPurchaseWithoutAPriceAddsTheGramsAndLeavesTheCostPerGram() = runTest {
        data.stock.recordPurchase("t1", TeaPurchase(20.0, null, LocalDate.of(2026, 11, 8)))

        assertThat(purchases().single().priceCents).isNull()
        assertThat(summary().remainingGrams).isWithin(1e-9).of(115.5)
        assertThat(summary().costPerGramCents!!).isWithin(1e-9).of(8.5)
    }

    @Test
    fun anEarlierDayIsThatDayAtNoon() = runTest {
        data.stock.recordPurchase("t1", TeaPurchase(50.0, null, LocalDate.of(2026, 11, 2)))

        val noon = LocalDate.of(2026, 11, 2).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertThat(purchases().single().occurredAtMillis).isEqualTo(noon)
    }

    @Test
    fun aPurchaseRestoresAnArchivedTeaFromTheArchive() = runTest {
        data.teas.saveTea(data.teas.getTea("t1")!!.copy(isArchived = true))

        data.stock.recordPurchase("t1", TeaPurchase(50.0, null, LocalDate.of(2026, 11, 8)))

        assertThat(data.teas.getTea("t1")!!.isArchived).isFalse()
        assertThat(purchases()).hasSize(1)
    }

    @Test
    fun aPurchaseLeavesAnActiveTeaAsItIs() = runTest {
        val before = data.teas.getTea("t1")!!

        data.stock.recordPurchase("t1", TeaPurchase(50.0, null, LocalDate.of(2026, 11, 8)))

        assertThat(data.teas.getTea("t1")).isEqualTo(before)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aDayInTheFutureIsRefused() = runTest {
        try {
            data.stock.recordPurchase("t1", TeaPurchase(50.0, null, LocalDate.of(2026, 11, 9)))
        } finally {
            assertThat(purchases()).isEmpty()
        }
    }
}
