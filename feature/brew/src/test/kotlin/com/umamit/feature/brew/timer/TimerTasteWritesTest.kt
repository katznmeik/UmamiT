package com.umamit.feature.brew.timer

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.TasteAxis
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The timer's taste writes (JRN-03, JRN-05, D-114; T4 review MIN-4, MIN-5): one after another in the order given, each
 * completed when the screen's scope is cancelled meanwhile (rule 8), a failing write never stopping the later ones,
 * and every refused or failed write counted so the sheet shows the stored values again. On Robolectric for the log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerTasteWritesTest {

    private val scheduler = TestCoroutineScheduler()
    private val job = Job()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + job)

    /** Every call in the order the store received it, as „method:argument". */
    private val received = mutableListOf<String>()

    /** What each call returns, by its argument; a missing one is [TastingWrite.SAVED]. */
    private val answers = mutableMapOf<String, () -> TastingWrite>()

    private val tasting = object : SessionTasting by FakeSessions(FakeCollection()).tasting {
        override suspend fun setInfusionTaste(
            sessionId: String,
            position: Int,
            axis: TasteAxis,
            value: Int?,
        ): TastingWrite = answer("taste:$sessionId/$position/$axis=$value")

        override suspend fun clearInfusionTaste(sessionId: String, position: Int): TastingWrite =
            answer("clear:$sessionId/$position")

        override suspend fun setInfusionNote(sessionId: String, position: Int, note: String?): TastingWrite =
            answer("note:$sessionId/$position=$note")

        private suspend fun answer(call: String): TastingWrite {
            // The first write is slow: a later one must still wait for it.
            if (received.isEmpty()) delay(SLOW_MS)
            received += call
            return answers[call]?.invoke() ?: TastingWrite.SAVED
        }
    }

    private val writes = TimerTasteWrites(tasting, scope)

    @Test
    fun `the writes run one after another in the order given`() {
        writes.setTaste("s1", 0, TasteAxis.UMAMI, 4)
        writes.setNote("s1", 0, "Süß")
        writes.clearTaste("s1", 0)
        writes.setTaste("s1", 0, TasteAxis.BODY, 2)
        scheduler.advanceUntilIdle()

        assertThat(received).containsExactly(
            "taste:s1/0/UMAMI=4",
            "note:s1/0=Süß",
            "clear:s1/0",
            "taste:s1/0/BODY=2",
        ).inOrder()
        assertThat(writes.refusals.value).isEqualTo(0)
    }

    @Test
    fun `each write completes when the scope is cancelled meanwhile (rule 8)`() {
        writes.setTaste("s1", 0, TasteAxis.UMAMI, 4)
        writes.setNote("s1", 0, "Kräftig")
        // The sheet closed on an alert and the screen was left: the view model is cleared before the writes ran.
        job.cancel()
        scheduler.advanceUntilIdle()

        assertThat(received).containsExactly("taste:s1/0/UMAMI=4", "note:s1/0=Kräftig").inOrder()
    }

    @Test
    fun `a failing or refused write never stops the later ones, and each is counted`() {
        answers["taste:s1/0/UMAMI=4"] = { throw IOException("disk full") }
        answers["note:s1/0=Süß"] = { TastingWrite.NOT_AN_INFUSION }
        writes.setTaste("s1", 0, TasteAxis.UMAMI, 4)
        writes.setNote("s1", 0, "Süß")
        writes.setTaste("s1", 0, TasteAxis.AROMA, 3)
        scheduler.advanceUntilIdle()

        assertThat(received).containsExactly("taste:s1/0/UMAMI=4", "note:s1/0=Süß", "taste:s1/0/AROMA=3").inOrder()
        assertThat(writes.refusals.value).isEqualTo(2)
    }

    @Test
    fun `a note longer than the limit is cut to 200 characters before it is written`() {
        writes.setNote("s1", 0, "a".repeat(250))
        scheduler.advanceUntilIdle()

        assertThat(received).containsExactly("note:s1/0=${"a".repeat(200)}")
    }

    private companion object {
        const val SLOW_MS = 1_000L
    }
}
