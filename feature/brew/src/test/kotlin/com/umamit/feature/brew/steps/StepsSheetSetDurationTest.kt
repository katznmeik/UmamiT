package com.umamit.feature.brew.steps

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepStatus
import org.junit.Test

/** „Dauer wählen" sets a step's duration in one go, within PRF-04's bounds and rounding (SCR-31, #141). */
class StepsSheetSetDurationTest {

    private fun state(status: StepStatus = StepStatus.PENDING) = StepsSheetState(
        StepsSheetInput(listOf(StepsSheetStep(StepKind.INFUSION, 300, 60, status)), true, 60, 10),
    )

    private fun set(seconds: Int): Int = state().apply { setDuration(0, seconds) }.steps.single().plannedSeconds

    @Test
    fun `a duration on the grid is taken as it is`() {
        assertThat(set(600)).isEqualTo(600)
        assertThat(set(595)).isEqualTo(595)
        assertThat(set(3_720)).isEqualTo(3_720)
    }

    @Test
    fun `below 10 min it rounds down to 5 s, from 10 min to whole minutes`() {
        assertThat(set(63)).isEqualTo(60)
        assertThat(set(599)).isEqualTo(595)
        assertThat(set(659)).isEqualTo(600)
    }

    @Test
    fun `it stays within 5 s and 24 h`() {
        assertThat(set(0)).isEqualTo(5)
        assertThat(set(3)).isEqualTo(5)
        assertThat(set(100_000)).isEqualTo(86_400)
    }

    @Test
    fun `a running or done step keeps its duration`() {
        listOf(StepStatus.STARTED, StepStatus.DONE).forEach { status ->
            val state = state(status).apply { setDuration(0, 600) }
            assertThat(state.steps.single().plannedSeconds).isEqualTo(300)
        }
    }

    @Test
    fun `a new duration is an unsaved edit`() {
        val state = state()
        state.setDuration(0, 600)
        assertThat(state.isDirty).isTrue()
        assertThat(state.editRequest().steps.single().plannedSeconds).isEqualTo(600)
    }
}
