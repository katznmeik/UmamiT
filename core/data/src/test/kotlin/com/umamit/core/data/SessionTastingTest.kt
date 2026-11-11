package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.WallClock
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteProfile
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The tasting writes (05 §3.7 „Tasting writes", §3.8, JRN-02–JRN-05, D-112, #223): each input's bounds, the 20
 * descriptors, a `COMPLETED` session only, and nothing but the session's tasting columns, its descriptor rows and
 * its `updated_at` changes — never its steps, stock, cost or `active_brew`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionTastingTest {
    private val data = TestDatabase()
    private var now = WRITE_AT
    private val clock = object : WallClock {
        override fun nowMs(): Long = now

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val tasting = RoomSessionTasting(data.db, clock)

    @Before
    fun setUp() = runTest {
        data.teas.saveTea(tea("t1"))
        completed("done")
        data.sessions.startSession(SessionStart("running", snapshotOf(), listOf(infusion(60)), 10, 5_000, "UTC"))
    }

    @After
    fun tearDown() = data.close()

    private suspend fun completed(id: String) {
        data.sessions.startSession(SessionStart(id, snapshotOf(), listOf(infusion(60), infusion(20)), 10, 2_000, "UTC"))
        data.sessions.recordStep(id, 0, StepRecord(60, 61, 0, 1, 2_000, 63_000, false, false))
        data.sessions.completeSession(id, autoFinished = false)
    }

    private suspend fun session(id: String = "done") = checkNotNull(data.sessions.getSession(id))

    @Test
    fun `a rating is set in half steps from 0,5 to 5,0 and can be removed`() = runTest {
        listOf(0.5, 3.5, 5.0).forEach { rating ->
            assertThat(tasting.setRating("done", rating)).isEqualTo(TastingWrite.SAVED)
            assertThat(session().rating).isEqualTo(rating)
        }

        assertThat(tasting.setRating("done", null)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().rating).isNull()
    }

    @Test
    fun `a rating off the half steps or out of bounds writes nothing`() = runTest {
        tasting.setRating("done", 4.0)

        listOf(0.0, 0.25, 3.3, 5.5, -1.0, Double.NaN).forEach { rating ->
            assertThat(tasting.setRating("done", rating)).isEqualTo(TastingWrite.INVALID)
        }
        assertThat(session().rating).isEqualTo(4.0)
    }

    @Test
    fun `a taste value is set per axis from 0 to 5, unset alone, and Zuruecksetzen clears all six`() = runTest {
        assertThat(tasting.setTaste("done", TasteAxis.UMAMI, 5)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setTaste("done", TasteAxis.BITTERNESS, 0)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setTaste("done", TasteAxis.AROMA, 3)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().taste).isEqualTo(TasteProfile(umami = 5, bitterness = 0, aroma = 3))

        assertThat(tasting.setTaste("done", TasteAxis.AROMA, null)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().taste).isEqualTo(TasteProfile(umami = 5, bitterness = 0))

        assertThat(tasting.clearTaste("done")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().taste).isEqualTo(TasteProfile.EMPTY)
    }

    @Test
    fun `every axis has its own column`() = runTest {
        TasteAxis.entries.forEachIndexed { i, axis -> tasting.setTaste("done", axis, i) }

        val taste = session().taste
        assertThat(TasteAxis.entries.map { taste[it] }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun `a taste value beyond 0 to 5 writes nothing`() = runTest {
        tasting.setTaste("done", TasteAxis.BODY, 2)

        assertThat(tasting.setTaste("done", TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setTaste("done", TasteAxis.BODY, -1)).isEqualTo(TastingWrite.INVALID)
        assertThat(session().taste).isEqualTo(TasteProfile(body = 2))
    }

    @Test
    fun `notes are trimmed, blank notes are stored as none, and 5000 characters is the limit`() = runTest {
        assertThat(tasting.setNotes("done", "  Süß, wenig Bitterkeit \n")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().notes).isEqualTo("Süß, wenig Bitterkeit")

        assertThat(tasting.setNotes("done", "   \n ")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().notes).isNull()

        val longest = "a".repeat(5_000)
        assertThat(tasting.setNotes("done", " $longest ")).isEqualTo(TastingWrite.SAVED)
        assertThat(session().notes).isEqualTo(longest)

        assertThat(tasting.setNotes("done", longest + "b")).isEqualTo(TastingWrite.INVALID)
        assertThat(session().notes).isEqualTo(longest)

        assertThat(tasting.setNotes("done", null)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().notes).isNull()
    }

    @Test
    fun `descriptors are ticked in order, ticked twice once, and unticked`() = runTest {
        listOf("vegetal.spinat", "marine.nori", "vegetal.spinat").forEach {
            now += 1_000
            assertThat(tasting.setDescriptor("done", it, ticked = true)).isEqualTo(TastingWrite.SAVED)
        }
        assertThat(session().descriptorKeys).containsExactly("vegetal.spinat", "marine.nori").inOrder()

        assertThat(tasting.setDescriptor("done", "vegetal.spinat", ticked = false)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setDescriptor("done", "never.ticked", ticked = false)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().descriptorKeys).containsExactly("marine.nori")
        assertThat(tasting.setDescriptor("done", " ", ticked = true)).isEqualTo(TastingWrite.INVALID)
    }

    @Test
    fun `a tick of a key the vocabulary lacks is refused, an untick of a stored unknown key is not`() = runTest {
        data.db.tastingDao().insertDescriptor(SessionDescriptorEntity("done", "future.thing", createdAt = 1))

        assertThat(tasting.setDescriptor("done", "future.other", ticked = true)).isEqualTo(TastingWrite.INVALID)
        assertThat(session().descriptorKeys).containsExactly("future.thing")

        assertThat(tasting.setDescriptor("done", "future.thing", ticked = false)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().descriptorKeys).isEmpty()
    }

    @Test
    fun `a 21st descriptor is refused, while a ticked one and a freed place still work`() = runTest {
        val keys = TasteDescriptor.entries.map { it.key }
        keys.take(20).forEach {
            assertThat(tasting.setDescriptor("done", it, ticked = true)).isEqualTo(TastingWrite.SAVED)
        }

        assertThat(tasting.setDescriptor("done", keys[20], ticked = true)).isEqualTo(TastingWrite.DESCRIPTOR_LIMIT)
        assertThat(tasting.setDescriptor("done", keys[19], ticked = true)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().descriptorKeys).hasSize(20)
        assertThat(session().descriptorKeys).doesNotContain(keys[20])

        tasting.setDescriptor("done", keys[0], ticked = false)
        assertThat(tasting.setDescriptor("done", keys[20], ticked = true)).isEqualTo(TastingWrite.SAVED)
        assertThat(session().descriptorKeys).hasSize(20)
    }

    @Test
    fun `a write on a session in progress or a missing one changes nothing`() = runTest {
        val before = data.db.sessionDao().get("running")

        assertThat(tasting.setRating("running", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setTaste("running", TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.clearTaste("running")).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setNotes("running", "Notiz")).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setDescriptor("running", "marine.nori", ticked = true)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(
            tasting.setDescriptor("running", "marine.nori", ticked = false),
        ).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setRating("gone", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setDescriptor("gone", "marine.nori", ticked = true)).isEqualTo(TastingWrite.NOT_COMPLETED)

        assertThat(data.db.sessionDao().get("running")).isEqualTo(before)
        assertThat(data.db.tastingDao().descriptors("running")).isEmpty()
        assertThat(data.db.tastingDao().descriptors("gone")).isEmpty()
    }

    @Test
    fun `the writes touch only the tasting columns and updated_at, never steps, stock, cost or the timer`() = runTest {
        val before = checkNotNull(data.db.sessionDao().get("done"))
        val steps = data.db.sessionDao().steps("done")
        val stock = data.db.inventoryDao().eventsForTea("t1")
        val active = data.db.activeBrewDao().get(com.umamit.core.model.TimerSlot.BREW)

        tasting.setRating("done", 4.5)
        tasting.setTaste("done", TasteAxis.SWEETNESS, 4)
        tasting.setNotes("done", "Notiz")
        tasting.setDescriptor("done", "marine.nori", ticked = true)

        val after = checkNotNull(data.db.sessionDao().get("done"))
        assertThat(after).isEqualTo(
            before.copy(rating = 4.5, tasteSweetness = 4, notes = "Notiz", updatedAt = WRITE_AT),
        )
        assertThat(after.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(data.db.sessionDao().steps("done")).isEqualTo(steps)
        assertThat(data.db.inventoryDao().eventsForTea("t1")).isEqualTo(stock)
        assertThat(data.db.activeBrewDao().get(com.umamit.core.model.TimerSlot.BREW)).isEqualTo(active)
    }

    @Test
    fun `a descriptor tick sets the session's updated_at`() = runTest {
        tasting.setDescriptor("done", "marine.nori", ticked = true)

        assertThat(data.db.sessionDao().get("done")?.updatedAt).isEqualTo(WRITE_AT)
    }

    private companion object {
        const val WRITE_AT = 9_000_000L
    }
}
