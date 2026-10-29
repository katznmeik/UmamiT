package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The pre-alert per step from v0.2.1 (TMR-23, 04 §5, D-093 point 3; spec #81
 * seam 1): each step's lead N follows the automatic rule
 * N = min(B, 5 · ⌊d / 15⌋), with B the recipe's or global value frozen at the
 * session's start, unless the Schritte-Sheet set an explicit value.
 */
class PreAlertPerStepTest {

    /** A case of the table: a step of [seconds] with base [base] has the lead [lead] (null: none). */
    private data class Case(val seconds: Int, val base: Int, val lead: Int?)

    /** The lead of the step started in a fresh session with [settings]: as shown and as scheduled. */
    private fun leadAfterStart(step: PlannedStep, settings: PreAlertSettings): Pair<Int?, Long?> {
        val driver = EngineDriver()
        driver.send(TimerEvent.StartSession(SESSION_ID, listOf(step), settings, longStepsOnly = false))
        driver.send(TimerEvent.Start)
        val shown = BrewTimerEngine.preAlertLeadSeconds(driver.state)
        val scheduled = BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs?.let { driver.deadline - it }
        return shown to scheduled
    }

    private fun assertLead(label: String, step: PlannedStep, settings: PreAlertSettings, lead: Int?) {
        val (shown, scheduled) = leadAfterStart(step, settings)
        assertWithMessage("$label: shown").that(shown).isEqualTo(lead)
        assertWithMessage("$label: scheduled").that(scheduled).isEqualTo(lead?.let { it * 1_000L })
    }

    @Test
    fun `without an explicit value a step gets a third of its length in whole 5 s, at most B`() {
        // TMR-23's examples with B = 10 s: 0:10 → none, 0:20 → 5 s, 0:30 and longer → 10 s; ≥ 10 min never (TMR-18).
        val cases = listOf(
            Case(10, base = 10, lead = null),
            Case(14, base = 10, lead = null),
            Case(15, base = 10, lead = 5),
            Case(20, base = 10, lead = 5),
            Case(29, base = 10, lead = 5),
            Case(30, base = 10, lead = 10),
            Case(60, base = 10, lead = 10),
            Case(599, base = 10, lead = 10),
            Case(600, base = 10, lead = null),
            Case(4 * 3_600, base = 10, lead = null),
            Case(10, base = 20, lead = null),
            Case(14, base = 20, lead = null),
            Case(15, base = 20, lead = 5),
            Case(20, base = 20, lead = 5),
            Case(29, base = 20, lead = 5),
            Case(30, base = 20, lead = 10),
            Case(45, base = 20, lead = 15),
            Case(60, base = 20, lead = 20),
            Case(600, base = 20, lead = null),
            Case(10, base = 0, lead = null),
            Case(14, base = 0, lead = null),
            Case(15, base = 0, lead = null),
            Case(20, base = 0, lead = null),
            Case(29, base = 0, lead = null),
            Case(30, base = 0, lead = null),
            Case(60, base = 0, lead = null),
            Case(600, base = 0, lead = null),
            // 04 §12's boundaries 14/15/19/20/30/45 s with B = 5 and 30 s.
            Case(14, base = 5, lead = null),
            Case(15, base = 5, lead = 5),
            Case(19, base = 5, lead = 5),
            Case(20, base = 5, lead = 5),
            Case(30, base = 5, lead = 5),
            Case(45, base = 5, lead = 5),
            Case(14, base = 30, lead = null),
            Case(15, base = 30, lead = 5),
            Case(19, base = 30, lead = 5),
            Case(20, base = 30, lead = 5),
            Case(30, base = 30, lead = 10),
            Case(45, base = 30, lead = 15),
            Case(89, base = 30, lead = 25),
            Case(90, base = 30, lead = 30),
            Case(599, base = 30, lead = 30),
        )
        cases.forEach { (seconds, base, lead) ->
            val settings = PreAlertSettings.inherited(recipeSeconds = null, globalSeconds = base)
            assertLead("infusion of $seconds s with B = $base", infusion(seconds), settings, lead)
        }
    }

    @Test
    fun `B is the recipe's value when it has one, else the global setting`() {
        val recipe30 = PreAlertSettings.inherited(recipeSeconds = 30, globalSeconds = 10)
        val recipeOff = PreAlertSettings.inherited(recipeSeconds = 0, globalSeconds = 10)

        assertLead("45 s with the recipe's 30 s", infusion(45), recipe30, 15)
        assertLead("90 s with the recipe's 30 s", infusion(90), recipe30, 30)
        assertLead("60 s with the recipe's Aus", infusion(60), recipeOff, null)
    }

    @Test
    fun `an explicit value applies as set, unless the step is shorter than N + 5 s, a short rinse or long (E7)`() {
        // (step, explicit N, B, expected lead or null); Aus is the explicit 0.
        val cases = listOf(
            Triple(infusion(10), 5, 10) to 5,
            Triple(infusion(9), 5, 10) to null,
            Triple(infusion(15), 10, 10) to 10,
            Triple(infusion(14), 10, 10) to null,
            Triple(infusion(25), 20, 10) to 20,
            Triple(infusion(24), 20, 10) to null,
            Triple(infusion(35), 30, 10) to 30,
            Triple(infusion(34), 30, 10) to null,
            // Above B, and with B off: the explicit value is not capped.
            Triple(infusion(60), 20, 10) to 20,
            Triple(infusion(60), 15, 0) to 15,
            Triple(whisk(20), 15, 10) to 15,
            Triple(rinse(15), 10, 10) to 10,
            Triple(rinse(14), 5, 10) to null,
            Triple(infusion(599), 30, 10) to 30,
            Triple(infusion(600), 10, 10) to null,
            Triple(infusion(10), 0, 10) to null,
            Triple(infusion(20), 0, 10) to null,
            Triple(infusion(60), 0, 30) to null,
        )
        cases.forEach { (case, lead) ->
            val (step, explicit, base) = case
            val settings = PreAlertSettings(sessionOverrideSeconds = explicit, inheritedSeconds = base)
            assertLead("$step with N = $explicit, B = $base", step, settings, lead)
        }
    }

    @Test
    fun `the Schritte-Sheet's choice gives every step of the plan its own lead`() {
        val plan = listOf(rinse(15), infusion(60), infusion(20), whisk(30), infusion(14), infusion(600))
        // (choice, the lead of each step in order); B = 10 s.
        val cases = listOf(
            PreAlertChoice.Inherit to listOf(5, 10, 5, 10, null, null),
            PreAlertChoice.Seconds(10) to listOf(10, 10, 10, 10, null, null),
            PreAlertChoice.Seconds(20) to listOf(null, 20, null, 20, null, null),
            PreAlertChoice.Off to listOf(null, null, null, null, null, null),
        )
        cases.forEach { (choice, leads) ->
            val driver = sessionWith(infusion(60)).apply { send(TimerEvent.EditSteps(plan, choice)) }

            val shown = plan.indices.map {
                BrewTimerEngine.preAlertLeadSeconds(driver.state).also { driver.send(TimerEvent.SkipStep) }
            }

            assertWithMessage("$choice").that(shown).isEqualTo(leads)
        }
    }

    @Test
    fun `the quick timer follows the automatic rule with B = the global setting (TMR-22)`() {
        // (duration, global setting, expected lead or null); 04 §10: a 0:30 timer gets at most 10 s.
        val cases = listOf(
            Case(5, base = 10, lead = null),
            Case(14, base = 10, lead = null),
            Case(15, base = 10, lead = 5),
            Case(20, base = 10, lead = 5),
            Case(30, base = 10, lead = 10),
            Case(30, base = 30, lead = 10),
            Case(60, base = 30, lead = 20),
            Case(120, base = 15, lead = 15),
            Case(300, base = 30, lead = 30),
            Case(90, base = 0, lead = null),
            Case(600, base = 10, lead = null),
        )
        cases.forEach { (seconds, base, lead) ->
            val driver = EngineDriver()
            driver.send(TimerEvent.StartQuickTimer(durationSeconds = seconds, globalPreAlertSeconds = base))
            val label = "quick timer of $seconds s with B = $base"
            assertWithMessage("$label: ready").that(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(lead)

            driver.send(TimerEvent.Start)

            assertWithMessage("$label: running").that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs)
                .isEqualTo(lead?.let { driver.deadline - it * 1_000L })
        }
    }

    @Test
    fun `a pre-alert changed mid-step follows the running step's new N`() {
        // A 20 s infusion, B = 10 s, started with [before] and edited 2 s in to [after]: (before, after, lead).
        val cases = listOf(
            Triple(PreAlertChoice.Seconds(10), PreAlertChoice.Inherit, 5),
            Triple(PreAlertChoice.Seconds(10), PreAlertChoice.Seconds(15), 15),
            Triple(PreAlertChoice.Seconds(10), PreAlertChoice.Seconds(5), 5),
            Triple(PreAlertChoice.Seconds(10), PreAlertChoice.Seconds(20), null),
            Triple(PreAlertChoice.Seconds(10), PreAlertChoice.Off, null),
            Triple(PreAlertChoice.Inherit, PreAlertChoice.Seconds(10), 10),
            Triple(PreAlertChoice.Off, PreAlertChoice.Inherit, 5),
        )
        cases.forEach { (before, after, lead) ->
            val driver = sessionWith(infusion(20)).apply {
                send(TimerEvent.EditSteps(listOf(infusion(20)), before))
                send(TimerEvent.Start)
            }
            val deadline = driver.deadline
            driver.clocks.advanceSeconds(2)

            val effects = driver.send(TimerEvent.EditSteps(listOf(infusion(20)), after))

            val label = "$before → $after"
            val expected = lead?.let { deadline - it * 1_000L }
            assertWithMessage("$label: shown").that(BrewTimerEngine.preAlertLeadSeconds(driver.state)).isEqualTo(lead)
            assertWithMessage("$label: scheduled")
                .that(effects).contains(TimerEffect.ScheduleAlarms(DueTimes(expected, deadline)))
            driver.advanceTo(expected ?: deadline)
            val tick = driver.send(TimerEvent.PreAlertReached)
            assertWithMessage("$label: tick").that(TimerEffect.PlayPreAlert in tick).isEqualTo(lead != null)
        }
    }

    /** An edit [atMs] into a running 20 s infusion from [before] to [after]; the pre-alert is then due at [dueAtMs]. */
    private data class MidStepEdit(
        val before: PreAlertChoice,
        val atMs: Long,
        val after: PreAlertChoice,
        val dueAtMs: Long?,
    )

    @Test
    fun `a new N whose time has passed is skipped, but a due pre-alert stays due while the step keeps one`() {
        // B = 10 s; times from the step's start.
        val cases = listOf(
            // The automatic 5 s is due at 15 s; at 12 s the new 10 s is already past: skipped (04 §5).
            MidStepEdit(PreAlertChoice.Inherit, 12_000, PreAlertChoice.Seconds(10), dueAtMs = null),
            // The explicit 10 s came due at 10 s and its tick is queued; the automatic 5 s keeps it (D-054).
            MidStepEdit(PreAlertChoice.Seconds(10), 10_005, PreAlertChoice.Inherit, dueAtMs = 10_000),
            MidStepEdit(PreAlertChoice.Seconds(10), 10_005, PreAlertChoice.Off, dueAtMs = null),
        )
        cases.forEach { case ->
            val driver = sessionWith(infusion(20)).apply {
                send(TimerEvent.EditSteps(listOf(infusion(20)), case.before))
                send(TimerEvent.Start)
            }
            val started = driver.deadline - 20_000
            driver.advanceTo(started + case.atMs)

            driver.send(TimerEvent.EditSteps(listOf(infusion(20)), case.after))

            assertWithMessage("$case").that(BrewTimerEngine.dueTimes(driver.state).preAlertAtElapsedMs)
                .isEqualTo(case.dueAtMs?.let { started + it })
            assertWithMessage("$case").that((driver.state as TimerState.Running).preAlertPlayed).isFalse()
        }
    }
}
