package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TeaCategory
import com.umamit.core.timer.engine.PlannedStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The tasting fake follows Room's rules (05 §3.7 „Tasting writes", #223) and feeds the fake journal's summaries. */
class FakeSessionTastingTest {
    private val fakes = FakeSessions()

    private fun start(id: String) = SessionStart(
        id = id,
        snapshot = SessionSnapshot(
            "t1", "Hisui", null, null, null, TeaCategory.GREEN, null, null, BrewMethod.KYUSU,
            null, null, leafGrams = null, waterMl = 200, baseTempC = 60,
        ),
        steps = listOf(PlannedStep(StepKind.INFUSION, 60, 60)),
        preAlertSeconds = 10,
        startedAtMs = 1_000,
        zoneId = "UTC",
    )

    private val keys = TasteDescriptor.entries.map { it.key }

    @Test
    fun `a tick of a key the vocabulary lacks is refused, an untick of any key is not`() = runTest {
        fakes.sessions.startSession(start("done"))
        fakes.sessions.completeSession("done", autoFinished = false)
        fakes.storeDescriptorKey("done", "future.thing")
        val tasting = fakes.tasting

        assertThat(tasting.setDescriptor("done", "future.other", ticked = true)).isEqualTo(TastingWrite.INVALID)
        assertThat(fakes.sessions.getSession("done")!!.descriptorKeys).containsExactly("future.thing")

        assertThat(tasting.setDescriptor("done", "future.thing", ticked = false)).isEqualTo(TastingWrite.SAVED)
        assertThat(fakes.sessions.getSession("done")!!.descriptorKeys).isEmpty()
    }

    @Test
    fun `writes on a completed session only, with the bounds, and the summary follows`() = runTest {
        fakes.sessions.startSession(start("done"))
        fakes.sessions.completeSession("done", autoFinished = false)
        fakes.sessions.startSession(start("running"))
        val tasting = fakes.tasting

        assertThat(tasting.setRating("running", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setRating("done", 4.2)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setRating("done", 4.5)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setTaste("done", TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setTaste("done", TasteAxis.BODY, 4)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setNotes("done", "  ")).isEqualTo(TastingWrite.SAVED)
        keys.take(20).forEach { tasting.setDescriptor("done", it, ticked = true) }
        assertThat(tasting.setDescriptor("done", keys[20], ticked = true)).isEqualTo(TastingWrite.DESCRIPTOR_LIMIT)

        val session = fakes.sessions.getSession("done")!!
        assertThat(session.rating).isEqualTo(4.5)
        assertThat(session.taste).isEqualTo(TasteProfile(body = 4))
        assertThat(session.notes).isNull()
        assertThat(session.descriptorKeys).hasSize(20)
        assertThat(fakes.sessions.getSession("running")!!.rating).isNull()
        assertThat(tasting.writes).isEqualTo(27)

        val summary = fakes.journal.observeTeaTasting("t1").first()
        assertThat(summary.tasteAverages).containsExactly(TasteAxis.BODY, 4.0)
        assertThat(summary.descriptorCounts.first()).isEqualTo(DescriptorCount(keys.take(20).min(), 1))
    }

    @Test
    fun `an invalid value is refused before the session is checked, as on Room`() = runTest {
        fakes.sessions.startSession(start("running"))
        val tasting = fakes.tasting

        assertThat(tasting.setRating("running", 4.2)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setTaste("running", TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setNotes("gone", "a".repeat(5_001))).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setDescriptor("running", " ", ticked = true)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setRating("running", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
    }
}
