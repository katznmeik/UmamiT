package com.umamit.core.testing

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The fake starter follows the controller's admission of v0.5.0 (GEN-03, TMR-21, TMR-22, D-108). */
class FakeBrewStarterTest {
    private val starter = FakeBrewStarter()

    private fun start(teaId: String, name: String) = BrewStart(
        snapshot = SessionSnapshot(
            teaId, name, null, null, null, TeaCategory.GREEN, null, null, BrewMethod.KYUSU,
            null, null, leafGrams = 4.0, waterMl = 200, baseTempC = 60,
        ),
        steps = listOf(PlannedStep(StepKind.INFUSION, 60, 60)),
        recipePreAlertSeconds = null,
    )

    @Test
    fun `a second tea starts as the second brew session, in front (D-108)`() = runTest {
        assertThat(starter.startSession(start("hisui", "Hisui"))).isEqualTo(TransitionOutcome.Accepted)

        assertThat(starter.startSession(start("kamille", "Kamille"))).isEqualTo(TransitionOutcome.Accepted)

        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Hisui")
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.teaName).isEqualTo("Kamille")
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.slot).isEqualTo(TimerSlot.BREW_2)
        assertThat(starter.state.value).isEqualTo(starter.activeIn(TimerSlot.BREW_2))
    }

    @Test
    fun `a tea with an active session is refused naming that session (D-108)`() = runTest {
        starter.startSession(start("hisui", "Hisui"))
        starter.startQuickTimer(90)

        val outcome = starter.startSession(start("hisui", "Hisui"))

        val session = checkNotNull(starter.activeIn(TimerSlot.BREW)).kind
        assertThat(outcome).isEqualTo(TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(session)))
        assertThat(starter.starts).hasSize(1)
    }

    @Test
    fun `any start while two timers are active is refused with the active slots (TMR-21)`() = runTest {
        starter.startSession(start("hisui", "Hisui"))
        starter.startQuickTimer(90)

        val brew = starter.startSession(start("kamille", "Kamille"))
        starter.finishActiveAndStartQuickTimer(60, ending = TimerSlot.QUICK)
        starter.startSession(start("tanabata", "Tanabata"))
        val quick = starter.startQuickTimer(30)

        val two = TransitionOutcome.Rejected(RejectionReason.TwoTimersActive(listOf(TimerSlot.BREW, TimerSlot.QUICK)))
        assertThat(brew).isEqualTo(two)
        assertThat(quick).isEqualTo(
            TransitionOutcome.Rejected(RejectionReason.ActiveTimerExists(SessionKind.Quick)),
        )
        val twoBrews = FakeBrewStarter()
        twoBrews.startSession(start("hisui", "Hisui"))
        twoBrews.startSession(start("kamille", "Kamille"))
        assertThat(twoBrews.startQuickTimer(30)).isEqualTo(
            TransitionOutcome.Rejected(RejectionReason.TwoTimersActive(listOf(TimerSlot.BREW, TimerSlot.BREW_2))),
        )
    }

    @Test
    fun `ending the chosen timer frees its slot for the new start, the other stays`() = runTest {
        starter.startSession(start("hisui", "Hisui"))
        starter.startSession(start("kamille", "Kamille"))

        val outcome = starter.finishActiveAndStart(start("tanabata", "Tanabata"), ending = TimerSlot.BREW)

        assertThat(outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Tanabata")
        assertThat(starter.activeIn(TimerSlot.BREW_2)?.teaName).isEqualTo("Kamille")

        starter.finishActiveAndStartQuickTimer(45, ending = TimerSlot.BREW_2)
        assertThat(starter.activeIn(TimerSlot.BREW_2)).isNull()
        assertThat(starter.activeIn(TimerSlot.QUICK)?.steps?.single()?.plannedSeconds).isEqualTo(45)
        assertThat(starter.activeIn(TimerSlot.BREW)?.teaName).isEqualTo("Tanabata")
    }
}
