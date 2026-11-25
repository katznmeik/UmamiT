package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.domain.stats.StatsCalculator
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StepKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Editing a stock event (INV-03, SCR-29, D-136 items 11–12) and the recompute of past session costs (INV-08) on the
 * database in memory, with invented teas: the event keeps its id, a refusal writes nothing, and an edited or deleted
 * purchase reprices the tea's finished sessions in the same write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockEditTest {
    private val now = Instant.parse("2026-11-24T10:00:00Z").toEpochMilli()
    private val data = TestDatabase(now = now)
    private val today = LocalDate.of(2026, 11, 24)
    private val opened = LocalDate.of(2026, 10, 1)
    private val initialAt = Instant.parse("2026-10-01T09:00:00Z").toEpochMilli()
    private val purchaseAt = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1").copy(openedDate = opened, isArchived = true))
        data.teas.saveTea(tea("t2"))
        // 100 g for 8,00 € and 50 g for 7,00 €: 15,00 € / 150 g = 10 ct per g.
        data.stock.addEvent(initial("e1", "t1", 100.0, 800, at = initialAt))
        data.stock.addEvent(purchase("p1", "t1", 50.0, 700, purchaseAt))
        data.stock.addEvent(InventoryEvent("a1", "t1", InventoryEventType.ADJUSTMENT, -15.0, purchaseAt + 1))
        data.stock.addEvent(InventoryEvent("b1", "t1", InventoryEventType.BREW, -4.0, purchaseAt + 2))
        data.stock.addEvent(initial("x1", "t2", 100.0, 2_000, at = initialAt))
        session("done", leaf = 4.0, cost = 40L)
        session("hand", leaf = 5.0, cost = 50L, status = SessionStatus.MANUAL)
        session("running", leaf = 4.0, cost = null, status = SessionStatus.IN_PROGRESS)
        session("noLeaf", leaf = null, cost = 33L)
        session("other", leaf = 4.0, cost = 80L, teaId = "t2")
    }

    @After
    fun tearDown() = data.close()

    @Test
    fun `a purchase gets new grams, price and day and keeps its id and type`() = runTest {
        assertThat(data.stock.updatePurchase("p1", 80.0, 1_200, LocalDate.of(2026, 11, 3))).isTrue()

        val event = event("p1")
        assertThat(event.type).isEqualTo(InventoryEventType.PURCHASE)
        assertThat(event.deltaGrams).isEqualTo(80.0)
        assertThat(event.priceCents).isEqualTo(1_200)
        assertThat(event.packageLabel).isEqualTo("80 g")
        assertThat(event.occurredAtMillis).isEqualTo(noon(LocalDate.of(2026, 11, 3)))
        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(161.0)
    }

    @Test
    fun `the first package is a purchase too and gets new grams, price and day`() = runTest {
        assertThat(data.stock.updatePurchase("e1", 120.0, 1_000, LocalDate.of(2026, 10, 2))).isTrue()

        val event = event("e1")
        assertThat(event.type).isEqualTo(InventoryEventType.INITIAL)
        assertThat(event.deltaGrams).isEqualTo(120.0)
        assertThat(event.priceCents).isEqualTo(1_000)
        assertThat(event.packageLabel).isEqualTo("120 g")
        assertThat(event.occurredAtMillis).isEqualTo(noon(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `a purchase left on its day keeps its time and its label for unchanged grams`() = runTest {
        assertThat(data.stock.updatePurchase("e1", 100.0, 1_000, opened)).isTrue()

        val event = event("e1")
        assertThat(event.priceCents).isEqualTo(1_000)
        assertThat(event.occurredAtMillis).isEqualTo(initialAt)
        assertThat(event.packageLabel).isEqualTo("100 g")
    }

    @Test
    fun `a purchase moved to today is at now`() = runTest {
        assertThat(data.stock.updatePurchase("p1", 50.0, 700, today)).isTrue()

        assertThat(event("p1").occurredAtMillis).isEqualTo(now)
    }

    @Test
    fun `an edit leaves the tea's opened date and archive flag alone`() = runTest {
        data.stock.updatePurchase("p1", 80.0, 1_200, LocalDate.of(2026, 11, 3))

        val tea = data.teas.getTea("t1")!!
        assertThat(tea.openedDate).isEqualTo(opened)
        assertThat(tea.isArchived).isTrue()
    }

    @Test
    fun `an edited price recomputes the finished sessions of the tea with a leaf weight`() = runTest {
        // 8,00 € + 22,00 € over 150 g: 20 ct per g.
        assertThat(data.stock.updatePurchase("p1", 50.0, 2_200, LocalDate.of(2026, 11, 1))).isTrue()

        assertThat(cost("done")).isEqualTo(80L)
        assertThat(cost("hand")).isEqualTo(100L)
        assertThat(cost("running")).isNull()
        assertThat(cost("noLeaf")).isEqualTo(33L)
        assertThat(cost("other")).isEqualTo(80L)
    }

    @Test
    fun `edited grams recompute the weighted cost per gram`() = runTest {
        // 8,00 € + 7,00 € over 100 g + 200 g: 5 ct per g.
        data.stock.updatePurchase("p1", 200.0, 700, LocalDate.of(2026, 11, 1))

        assertThat(cost("done")).isEqualTo(20L)
        assertThat(cost("hand")).isEqualTo(25L)
    }

    @Test
    fun `no priced purchase left gives the sessions no cost`() = runTest {
        data.stock.updatePurchase("e1", 100.0, null, opened)
        data.stock.updatePurchase("p1", 50.0, null, LocalDate.of(2026, 11, 1))

        assertThat(cost("done")).isNull()
        assertThat(cost("hand")).isNull()
        assertThat(cost("other")).isEqualTo(80L)
    }

    @Test
    fun `a deleted purchase recomputes the sessions from the rest`() = runTest {
        // Only 8,00 € / 100 g left: 8 ct per g.
        assertThat(data.stock.deleteEvent("p1")).isTrue()

        assertThat(cost("done")).isEqualTo(32L)
        assertThat(cost("hand")).isEqualTo(40L)
        assertThat(cost("other")).isEqualTo(80L)
    }

    @Test
    fun `deleting every priced purchase leaves the sessions without a cost`() = runTest {
        data.stock.deleteEvent("p1")
        data.stock.deleteEvent("e1")

        assertThat(cost("done")).isNull()
        assertThat(cost("hand")).isNull()
    }

    @Test
    fun `a correction's delete or edit leaves the session costs as brewed`() = runTest {
        data.stock.recordPurchase("t1", TeaPurchase(100.0, 5_000, today))
        assertThat(data.stock.updateCorrection("a1", 5.0, LocalDate.of(2026, 11, 2))).isTrue()
        assertThat(data.stock.deleteEvent("a1")).isTrue()

        assertThat(cost("done")).isEqualTo(40L)
        assertThat(cost("hand")).isEqualTo(50L)
    }

    @Test
    fun `a purchase added later leaves the old costs alone`() = runTest {
        data.stock.recordPurchase("t1", TeaPurchase(100.0, 5_000, today))

        assertThat(cost("done")).isEqualTo(40L)
        assertThat(cost("hand")).isEqualTo(50L)
    }

    @Test
    fun `a correction gets its signed difference and day`() = runTest {
        assertThat(data.stock.updateCorrection("a1", 5.0, LocalDate.of(2026, 11, 2))).isTrue()

        val event = event("a1")
        assertThat(event.type).isEqualTo(InventoryEventType.ADJUSTMENT)
        assertThat(event.deltaGrams).isEqualTo(5.0)
        assertThat(event.occurredAtMillis).isEqualTo(noon(LocalDate.of(2026, 11, 2)))
        assertThat(data.stock.observeStock("t1").first().remainingGrams).isWithin(1e-9).of(151.0)
    }

    @Test
    fun `refused edits write nothing`() = runTest {
        val before = snapshot()
        val tomorrow = today.plusDays(1)

        assertThrows { data.stock.updatePurchase("p1", 50.0, 700, tomorrow) }
        assertThrows { data.stock.updatePurchase("p1", 0.0, 700, today) }
        assertThrows { data.stock.updatePurchase("p1", -5.0, 700, today) }
        assertThrows { data.stock.updatePurchase("p1", Double.NaN, 700, today) }
        assertThrows { data.stock.updatePurchase("p1", 50.0, -1, today) }
        assertThrows { data.stock.updateCorrection("a1", 0.0, today) }
        assertThrows { data.stock.updateCorrection("a1", Double.NaN, today) }
        assertThrows { data.stock.updateCorrection("a1", 5.0, tomorrow) }
        assertThat(data.stock.updatePurchase("a1", 50.0, 700, today)).isFalse()
        assertThat(data.stock.updatePurchase("b1", 50.0, 700, today)).isFalse()
        assertThat(data.stock.updatePurchase("nope", 50.0, 700, today)).isFalse()
        assertThat(data.stock.updateCorrection("p1", 5.0, today)).isFalse()
        assertThat(data.stock.updateCorrection("b1", 5.0, today)).isFalse()
        assertThat(data.stock.updateCorrection("nope", 5.0, today)).isFalse()
        assertThat(data.stock.deleteEvent("b1")).isFalse()

        assertThat(snapshot()).isEqualTo(before)
    }

    @Test
    fun `Statistik reads the new money spent, money drunk and cost per litre`() = runTest {
        val stats =
            RoomStatsRepository(data.db.statsDao(), data.settings, PhotoFiles(File("photos")), Dispatchers.Default)
        val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val before = StatsCalculator(stats.observeInput().first(), clock).consumption(StatsRange.ALL)
        assertThat(before.drunkCents).isEqualTo(40L + 50L + 33L + 80L)

        // 20 ct per g: done 80 ct, hand 100 ct.
        data.stock.updatePurchase("p1", 50.0, 2_200, LocalDate.of(2026, 11, 1))

        val after = StatsCalculator(stats.observeInput().first(), clock).consumption(StatsRange.ALL)
        assertThat(after.drunkCents).isEqualTo(80L + 100L + 33L + 80L)
        // Money spent sums the purchases' prices: p1 now costs 22,00 € instead of 7,00 €.
        assertThat(after.moneyCents - before.moneyCents).isEqualTo(1_500L)
        // Each session pours 100 ml: (80 + 100 + 33 + 80) ct over 0,4 l.
        assertThat(after.costPerLiter!!.literCents).isEqualTo(733L)
    }

    private suspend fun event(id: String) = checkNotNull(data.db.inventoryDao().get(id)).let {
        InventoryEvent(
            id = it.id,
            teaId = it.teaId,
            type = it.type,
            deltaGrams = it.deltaGrams,
            occurredAtMillis = it.occurredAt,
            priceCents = it.priceCents,
            packageLabel = it.packageLabel,
        )
    }

    private suspend fun cost(sessionId: String) = checkNotNull(data.db.sessionDao().get(sessionId)).costCents

    private suspend fun snapshot() = listOf("t1", "t2").flatMap { data.db.inventoryDao().eventsForTea(it) } to
        listOf("done", "hand", "running", "noLeaf", "other").map { data.db.sessionDao().get(it) }

    private fun noon(day: LocalDate) = day.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()

    private suspend fun assertThrows(block: suspend () -> Unit) {
        val thrown = runCatching { block() }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun purchase(id: String, teaId: String, grams: Double, price: Long?, at: Long) =
        InventoryEvent(id, teaId, InventoryEventType.PURCHASE, grams, at, priceCents = price, packageLabel = "50 g")

    private suspend fun session(
        id: String,
        leaf: Double?,
        cost: Long?,
        status: SessionStatus = SessionStatus.COMPLETED,
        teaId: String = "t1",
    ) {
        data.db.sessionDao().insert(
            BrewSessionEntity(
                id = id,
                status = status,
                teaId = teaId,
                teaName = "Sencha $id",
                teaSecondaryName = null,
                teaType = "Sencha",
                styleKey = null,
                category = null,
                profileId = null,
                profileName = "Standard",
                method = BrewMethod.KYUSU,
                vesselId = null,
                vesselName = null,
                leafGrams = leaf,
                waterMl = 100,
                baseTempC = 70,
                preAlertSeconds = null,
                plannedStepCount = 1,
                costCents = cost,
                startedAt = purchaseAt + 10,
                endedAt = if (status == SessionStatus.IN_PROGRESS) null else purchaseAt + 20,
                zoneId = "UTC",
                rating = null,
                tasteUmami = null,
                tasteSweetness = null,
                tasteBitterness = null,
                tasteAstringency = null,
                tasteBody = null,
                tasteAroma = null,
                notes = null,
                autoFinished = false,
                timesOffered = false,
                timesApplied = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        data.db.sessionDao().insertSteps(listOf(pouredStep(id)))
    }

    /** One ended infusion: the session pours its 100 ml once. */
    private fun pouredStep(sessionId: String) = SessionStepEntity(
        id = "$sessionId-step",
        sessionId = sessionId,
        position = 0,
        kind = StepKind.INFUSION,
        plannedSeconds = 60,
        actualSeconds = 60,
        pausedSeconds = null,
        overtimeSeconds = null,
        tempC = 70,
        startedAt = null,
        endedAt = null,
        skipped = false,
        overtimeAutoAck = false,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
        preAlertSeconds = null,
    )
}
