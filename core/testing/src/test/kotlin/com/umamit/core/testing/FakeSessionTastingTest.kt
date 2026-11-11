package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionStart
import com.umamit.core.data.TastingWrite
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.DescriptorCount
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaRating
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
        (1..20).forEach { tasting.setDescriptor("done", "key.$it", ticked = true) }
        assertThat(tasting.setDescriptor("done", "key.21", ticked = true)).isEqualTo(TastingWrite.DESCRIPTOR_LIMIT)

        val session = fakes.sessions.getSession("done")!!
        assertThat(session.rating).isEqualTo(4.5)
        assertThat(session.taste).isEqualTo(TasteProfile(body = 4))
        assertThat(session.notes).isNull()
        assertThat(session.descriptorKeys).hasSize(20)
        assertThat(fakes.sessions.getSession("running")!!.rating).isNull()
        assertThat(tasting.writes).isEqualTo(27)

        assertThat(fakes.journal.observeTeaRatings().first()).containsExactly("t1", TeaRating(4.5, 1))
        val summary = fakes.journal.observeTeaTasting("t1").first()
        assertThat(summary.tasteAverages).containsExactly(TasteAxis.BODY, 4.0)
        assertThat(summary.descriptorCounts.first()).isEqualTo(DescriptorCount("key.1", 1))
    }
}
