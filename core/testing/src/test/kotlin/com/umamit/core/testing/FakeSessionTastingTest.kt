package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.Infusion
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TeaCategory
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The tasting fake follows Room's rules (05 §3.7 „Tasting writes", „Infusion tasting writes", #223, #242) and feeds
 * the fake journal's summaries.
 */
class FakeSessionTastingTest {
    private val fakes = FakeSessions()

    private fun start(id: String, steps: List<PlannedStep> = listOf(PlannedStep(StepKind.INFUSION, 60, 60))) =
        SessionStart(
            id = id,
            snapshot = SessionSnapshot(
                "t1", "Hisui", null, null, null, TeaCategory.GREEN, null, null, BrewMethod.KYUSU,
                null, null, leafGrams = null, waterMl = 200, baseTempC = 60,
            ),
            steps = steps,
            preAlertSeconds = 10,
            startedAtMs = 1_000,
            zoneId = "UTC",
        )

    private val keys = TasteDescriptor.entries.map { it.key }

    private val rinse = PlannedStep(StepKind.RINSE, 10, 60)
    private val poured = StepRecord(60, 60, 0, 0, 1_000, 61_000, false, false)
    private val infusion = PlannedStep(StepKind.INFUSION, 60, 60)

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
        fakes.sessions.recordStep("done", 0, poured)
        fakes.sessions.completeSession("done", autoFinished = false)
        fakes.sessions.startSession(start("running"))
        val tasting = fakes.tasting

        assertThat(tasting.setRating("running", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setRating("done", 4.2)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setRating("done", 4.5)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionTaste("done", 0, TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setInfusionTaste("done", 0, TasteAxis.BODY, 4)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setNotes("done", "  ")).isEqualTo(TastingWrite.SAVED)
        keys.take(20).forEach { tasting.setDescriptor("done", it, ticked = true) }
        assertThat(tasting.setDescriptor("done", keys[20], ticked = true)).isEqualTo(TastingWrite.DESCRIPTOR_LIMIT)

        val session = fakes.sessions.getSession("done")!!
        assertThat(session.rating).isEqualTo(4.5)
        assertThat(session.infusions.single().taste).isEqualTo(TasteProfile(body = 4))
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
        assertThat(tasting.setInfusionTaste("running", 0, TasteAxis.BODY, 6)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setInfusionNote("gone", 0, "a".repeat(201))).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setNotes("gone", "a".repeat(5_001))).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setDescriptor("running", " ", ticked = true)).isEqualTo(TastingWrite.INVALID)
        assertThat(tasting.setRating("running", 4.0)).isEqualTo(TastingWrite.NOT_COMPLETED)
    }

    @Test
    fun `an infusion takes taste and a note in a running or completed session, on an ended row only`() = runTest {
        fakes.sessions.startSession(start("live", listOf(rinse, infusion, infusion)))
        fakes.sessions.recordStep("live", 0, poured)
        fakes.sessions.recordStep("live", 1, poured)
        val tasting = fakes.tasting

        assertThat(tasting.setInfusionTaste("live", 0, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_AN_INFUSION)
        assertThat(tasting.setInfusionTaste("live", 2, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_AN_INFUSION)
        assertThat(tasting.setInfusionTaste("gone", 1, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.NOT_COMPLETED)
        assertThat(tasting.setInfusionTaste("live", 1, TasteAxis.UMAMI, 4)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionNote("live", 1, " Nori ")).isEqualTo(TastingWrite.SAVED)
        fakes.sessions.completeSession("live", autoFinished = false)
        assertThat(tasting.setInfusionTaste("live", 1, TasteAxis.BODY, 2)).isEqualTo(TastingWrite.SAVED)

        val stored = fakes.sessions.getSession("live")!!.infusions.single()
        assertThat(stored).isEqualTo(Infusion(1, 1, TasteProfile(umami = 4, body = 2), "Nori"))

        assertThat(tasting.clearInfusionTaste("live", 1)).isEqualTo(TastingWrite.SAVED)
        assertThat(tasting.setInfusionNote("live", 1, null)).isEqualTo(TastingWrite.SAVED)
        assertThat(fakes.sessions.getSession("live")!!.stepTastings).isEmpty()
    }
}
