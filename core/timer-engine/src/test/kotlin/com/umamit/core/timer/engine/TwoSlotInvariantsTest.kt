package com.umamit.core.timer.engine

import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerSlot
import org.junit.Test
import kotlin.random.Random

/**
 * The seeded invariant test of 04 §12 for several slots with the alert arbitration of 04 §9.3 (D-098, D-108):
 * timers side by side, random user events to any slot, the due events of every slot in the slot order (each
 * through [AlertArbitration.eventFor]), the player's reports, process deaths and reboots; after every event the
 * controller's [AlertArbitration.settle] and [PlayerSound.release], as each turn does. An independent player
 * oracle checks the sound: never two sounds, an alert taken from another only by a brew from the quick timer, a
 * pre-alert never over another slot's sound. And the states: never two slots ringing, a held alert waits only
 * while another rings and always rings before its silent end, every slot's due event handled.
 *
 * Two configurations: the v0.3.0 pair (one brew session and the quick timer), where the brew is also never held
 * and a quick alert never starts while the brew alerts; and all three slots (two brew sessions and the quick
 * timer), where the pure rules hold for any set of active slots.
 */
class TwoSlotInvariantsTest {

    @Test
    fun `random two-timer sequences keep the arbitration invariants`() {
        repeat(RUNS) { seed -> Run(Random(seed), seed, PAIR).go() }

        assertWithMessage("arbitration events accepted").that(accepted)
            .containsAtLeast("AlertHold", "AlertRingStart", "RecoverHeld", "AlertCompleted", "OvertimeTimeout")
        assertWithMessage("situations reached").that(situations).containsAtLeast(
            "preemption",
            "held at deadline",
            "released",
            "pre-alert waited, then played",
            "pre-alert waited, then consumed",
            "quick silent end after ringing",
            "both running",
        )
    }

    @Test
    fun `random three-slot sequences keep the arbitration invariants`() {
        repeat(RUNS) { seed -> Run(Random(seed), seed, TimerSlot.entries).go() }

        assertWithMessage("arbitration events accepted").that(accepted)
            .containsAtLeast("AlertHold", "AlertRingStart", "RecoverHeld", "AlertCompleted", "OvertimeTimeout")
        assertWithMessage("situations reached").that(situations).containsAtLeast(
            "preemption",
            "held at deadline",
            "brew held at deadline",
            "released",
            "brew released",
            "brew silent end after ringing",
            "pre-alert waited, then played",
            "pre-alert waited, then consumed",
            "two brews running",
        )
    }

    private val accepted = mutableSetOf<String>()
    private val situations = mutableSetOf<String>()

    /** What the one player physically sounds, from the commands the controller would send it. */
    private sealed interface Playing {
        val slot: TimerSlot

        data class Alert(override val slot: TimerSlot, val sinceMs: Long) : Playing

        data class PreAlert(override val slot: TimerSlot, val untilMs: Long) : Playing
    }

    private inner class Run(private val random: Random, private val seed: Int, private val slots: List<TimerSlot>) {
        private val clocks = VirtualClocks()
        private val states = slots.associateWith<TimerSlot, TimerState> { TimerState.Idle }.toMutableMap()
        private val brewSlots = slots.filter { it.isBrew }
        private var sound = PlayerSound(RING_MS)
        private var playing: Playing? = null
        private var n = 0
        private var label = ""

        /** The v0.3.0 pair: one brew slot, which is never held. */
        private val pair = brewSlots.size == 1

        fun go() {
            repeat(EVENTS_PER_RUN) {
                n = it
                advanceClock()
                when (val pick = random.nextInt(100)) {
                    in 0..5 -> send(brewSlots.random(random), randomSession())
                    in 6..9 -> send(TimerSlot.QUICK, randomQuick())
                    in 10..13 -> alongside()
                    in 14..40 -> send(slots.random(random), userEvents.random(random))
                    in 41..60 -> dueEvents()
                    in 61..75 -> onTime()
                    in 76..88 -> playerReports()
                    in 89..94 -> processRestart(reboot = false)
                    else -> processRestart(reboot = pick == 99)
                }
                checkStates()
            }
        }

        /** The due events of every slot, in the slot order, as the alarm and the wake-up deliver them (04 §4). */
        private fun dueEvents() {
            slots.forEach { slot ->
                BrewTimerEngine.dueEvents(states.getValue(slot), clocks.now()).forEach { send(slot, it) }
            }
        }

        /**
         * The next alarm or wake-up comes on time: the clock moves to the earliest due time of any slot, or to
         * the end of a pre-alert another one waits for, and that turn runs (04 §4).
         */
        private fun onTime() {
            val next = slots.flatMap { slot ->
                val due = BrewTimerEngine.dueTimes(states.getValue(slot))
                listOfNotNull(due.preAlertAtElapsedMs, due.deadlineElapsedMs, due.overtimeTimeoutAtElapsedMs)
            }.plus(listOfNotNull(sound.nextReleaseAtElapsedMs())).filter { it >= clocks.elapsed }.minOrNull()
            next?.let { clocks.advance(it - clocks.elapsed + random.nextLong(0, 3)) }
            dueEvents()
            release()
        }

        /**
         * A second timer while a brew step runs, ending within a few seconds of it, so the two timers' pre-alerts
         * and deadlines meet (S1): Start's *Schnelltimer*, or with two brew slots also a second brew session.
         */
        private fun alongside() {
            val running = brewSlots.firstNotNullOfOrNull { slot ->
                (states.getValue(slot) as? TimerState.Running)?.let { slot to it }
            } ?: return send(TimerSlot.QUICK, randomQuick())
            val (brewSlot, step) = running
            val left = ((step.deadlineElapsedMs - clocks.elapsed) / 1_000).toInt()
            val seconds = (left + random.nextInt(-4, 5)).coerceIn(15, 3_000)
            val secondBrew = brewSlots.firstOrNull { it != brewSlot }?.takeIf { random.nextBoolean() }
            if (secondBrew == null) {
                send(
                    TimerSlot.QUICK,
                    TimerEvent.StartQuickTimer(seconds, globalPreAlertSeconds = 10, preAlertSeconds = null),
                )
                send(TimerSlot.QUICK, TimerEvent.Start)
            } else {
                send(secondBrew, session(PlannedStep(StepKind.INFUSION, seconds, tempC = 60, randomPreAlert())))
                send(secondBrew, TimerEvent.Start)
            }
        }

        /** The player ran an alert's whole cycle and reports it (at least 15 s, Normal). */
        private fun playerReports() {
            val alert = playing as? Playing.Alert ?: return
            if (clocks.elapsed - alert.sinceMs < ALERT_CYCLE_MS) return
            playing = null
            sound.alertEnded(alert.slot)
            send(alert.slot, TimerEvent.AlertCompleted)
        }

        /**
         * The process died (and maybe the phone rebooted): a new one starts silent with every slot idle, then
         * restores each slot's persisted timer and recovers it, in the slot order, as the controller does.
         */
        private fun processRestart(reboot: Boolean) {
            if (reboot) clocks.reboot(offMs = random.nextLong(0, 900_000), upMs = 30_000)
            sound = PlayerSound(RING_MS)
            playing = null
            val persisted = states.toMap()
            slots.forEach { states[it] = TimerState.Idle }
            slots.forEach { slot ->
                states[slot] = persisted.getValue(slot)
                send(slot, TimerEvent.Recover)
            }
        }

        /** One event of [slot] in a turn, then the arbitration and the waiting pre-alerts, as the controller does. */
        private fun send(slot: TimerSlot, original: TimerEvent) {
            val event = AlertArbitration.eventFor(slot, original, states)
            if (event != original && event == TimerEvent.AlertHold && states.getValue(slot) is TimerState.Running) {
                situations += "held at deadline"
                if (slot.isBrew) situations += "brew held at deadline"
            }
            if (apply(slot, event)) settle()
            release()
        }

        /** The controller settles after every accepted event, until the slots agree. */
        private fun settle() {
            repeat(slots.size * 2) {
                val settle = AlertArbitration.settle(states) ?: return
                if (settle.event == TimerEvent.AlertHold) situations += "preemption"
                if (settle.event == TimerEvent.AlertRingStart) {
                    situations += "released"
                    if (settle.slot.isBrew) situations += "brew released"
                }
                if (!apply(settle.slot, settle.event)) return
            }
            error("seed $seed, event $n: the arbitration does not settle in $states")
        }

        /** [event] to [slot]; true when it was accepted. */
        private fun apply(slot: TimerSlot, event: TimerEvent): Boolean {
            val before = states.getValue(slot)
            val now = clocks.now()
            val transition = BrewTimerEngine.reduce(before, event, now)
            label = "seed $seed, event $n: $event to $slot in ${before::class.simpleName}"
            if (transition.outcome != TransitionOutcome.Accepted) return false
            accepted += event::class.simpleName.orEmpty()
            states[slot] = transition.state
            checkSilentEnd(slot, before, event, transition.state)
            transition.effects.forEach { effect -> play(slot, effect, transition.state) }
            checkSnapshot(slot)
            return true
        }

        /** The controller's side of the player for one effect of [slot], against the oracle. */
        private fun play(slot: TimerSlot, effect: TimerEffect, state: TimerState) {
            val now = clocks.elapsed
            when (effect) {
                TimerEffect.StartAlert -> {
                    val other = sounding()?.takeIf { it.slot != slot }
                    if (other is Playing.Alert) {
                        assertWithMessage("$label: only a brew's alert takes the player, and from the quick timer's")
                            .that(slot.isBrew && !other.slot.isBrew).isTrue()
                    }
                    if (!slot.isBrew) {
                        assertWithMessage("$label: a quick alert never starts while a brew rings")
                            .that(brewSlots.none { states.getValue(it).rings }).isTrue()
                    }
                    if (pair && !slot.isBrew) {
                        assertWithMessage("$label: a quick alert never starts while the brew alerts")
                            .that(states.getValue(TimerSlot.BREW)).isNotInstanceOf(TimerState.Alerting::class.java)
                    }
                    sound.alertStarted(slot)
                    playing = Playing.Alert(slot, now)
                }

                TimerEffect.StopAlert -> {
                    val other = sounding()?.takeIf { it.slot != slot }
                    val stops = sound.mayStop(slot, now)
                    assertWithMessage("$label: a stop never silences another slot's sound")
                        .that(stops).isEqualTo(other == null)
                    if (stops) playing = null
                }

                TimerEffect.PlayPreAlert -> {
                    val deadline = (state as TimerState.Running).deadlineElapsedMs
                    if (sound.preAlertDue(slot, deadline, now)) {
                        startPreAlert(slot, deadline)
                    } else {
                        assertWithMessage("$label: a pre-alert waits only for another slot's sound")
                            .that(sounding()?.slot.let { it != null && it != slot }).isTrue()
                    }
                }

                else -> Unit
            }
        }

        /** The waiting pre-alerts whose sound ended, as at the end of every turn. */
        private fun release() {
            val now = clocks.elapsed
            val before = sound.waitingPreAlerts.map { it.slot }.toSet()
            sound.release(now) { (states.getValue(it) as? TimerState.Running)?.deadlineElapsedMs }.forEach {
                situations += "pre-alert waited, then played"
                startPreAlert(it.slot, it.deadlineElapsedMs)
            }
            val gone = before - sound.waitingPreAlerts.map { it.slot }.toSet()
            if (gone.any { slot -> (playing as? Playing.PreAlert)?.slot != slot }) {
                situations += "pre-alert waited, then consumed"
            }
        }

        private fun startPreAlert(slot: TimerSlot, deadline: Long) {
            val now = clocks.elapsed
            val other = sounding()
            assertWithMessage("$label: a pre-alert never plays over another sound")
                .that(other == null || other.slot == slot).isTrue()
            assertWithMessage("$label: a pre-alert plays at least 1 s before its deadline")
                .that(deadline - now).isAtLeast(PRE_ALERT_MIN_LEFT_MS)
            playing = Playing.PreAlert(slot, now + RING_MS)
        }

        /** The oracle's sound now; a pre-alert has rung out after [RING_MS]. */
        private fun sounding(): Playing? =
            playing?.takeUnless { it is Playing.PreAlert && it.untilMs <= clocks.elapsed }

        /**
         * A step ended without the user (the overtime timeout, a late deadline, recovery): a held alert never ends
         * silently, a ringing one only 10 min after it began ringing, and a late deadline only if it could ring.
         */
        private fun checkSilentEnd(slot: TimerSlot, before: TimerState, event: TimerEvent, after: TimerState) {
            if (before !is TimerState.WithDeadline || after is TimerState.WithDeadline || event in userEvents) return
            val kind = if (slot.isBrew) "brew" else "quick"
            when (before) {
                is TimerState.Alerting -> {
                    assertWithMessage("$label: a held alert never ends silently").that(before.held).isFalse()
                    // On the wall clock, which survives a reboot (the wall moves only forward in this test).
                    val ringingFor = clocks.wall - checkNotNull(before.ringingSinceWallMs)
                    assertWithMessage("$label: an alert ends silently only 10 min after it began ringing")
                        .that(ringingFor).isAtLeast(OVERTIME_TIMEOUT_MS)
                    situations += "$kind silent end after ringing"
                }

                is TimerState.Running -> assertWithMessage(
                    "$label: a late deadline ends a timer only if it could ring",
                )
                    .that(AlertArbitration.eventFor(slot, TimerEvent.DeadlineReached, states))
                    .isEqualTo(TimerEvent.DeadlineReached)
            }
        }

        private fun checkStates() {
            val alerts = slots.mapNotNull { slot ->
                (states.getValue(slot) as? TimerState.Alerting)?.let { slot to it }
            }
            val ringing = alerts.filterNot { (_, alert) -> alert.held }
            assertWithMessage("$label: never two slots ringing").that(ringing.size).isAtMost(1)
            assertWithMessage("$label: a held alert waits only while another slot rings")
                .that(alerts.isNotEmpty() && ringing.isEmpty()).isFalse()
            if (pair) {
                val brewAlert = states.getValue(TimerSlot.BREW) as? TimerState.Alerting
                assertWithMessage("$label: the brew is never held").that(brewAlert?.held ?: false).isFalse()
                if (slots.all { states.getValue(it) is TimerState.Running }) situations += "both running"
            }
            if (brewSlots.size > 1 && brewSlots.all { states.getValue(it) is TimerState.Running }) {
                situations += "two brews running"
            }
            checkNothingOverdue()
        }

        /** Every slot's due event is accepted (as the arbitration hands it on), so no wake-up spins (D-048). */
        private fun checkNothingOverdue() {
            val now = clocks.now()
            slots.forEach { slot ->
                val state = states.getValue(slot)
                if (state is TimerState.Active && state.session.bootCount != now.bootCount) return@forEach
                BrewTimerEngine.dueEvents(state, now).forEach { due ->
                    val event = AlertArbitration.eventFor(slot, due, states)
                    assertWithMessage("$label: the due $event of $slot is accepted")
                        .that(BrewTimerEngine.reduce(state, event, now).outcome).isEqualTo(TransitionOutcome.Accepted)
                }
            }
        }

        private fun checkSnapshot(slot: TimerSlot) {
            val state = states.getValue(slot)
            assertWithMessage("$label: snapshot round trip of $slot")
                .that(BrewTimerEngine.restore(BrewTimerEngine.snapshot(state))).isEqualTo(state)
        }

        private fun advanceClock() {
            val targets = slots.flatMap { slot ->
                val due = BrewTimerEngine.dueTimes(states.getValue(slot))
                listOfNotNull(due.preAlertAtElapsedMs, due.deadlineElapsedMs, due.overtimeTimeoutAtElapsedMs)
            }.filter { it >= clocks.elapsed }
            when {
                targets.isNotEmpty() && random.nextInt(3) == 0 ->
                    clocks.advance((targets.random(random) + random.nextLong(-1, 2) - clocks.elapsed).coerceAtLeast(0))

                random.nextInt(10) == 0 -> clocks.advance(random.nextLong(5 * 60_000, 15 * 60_000))

                random.nextInt(4) == 0 -> clocks.advance(random.nextLong(0, 4_000))

                else -> clocks.advance(random.nextLong(0, 40_000))
            }
        }

        private fun randomSession() = session(
            *Array(random.nextInt(1, 4)) {
                PlannedStep(StepKind.INFUSION, random.nextInt(15, 120), tempC = 60, randomPreAlert())
            },
        )

        private fun session(vararg steps: PlannedStep) =
            TimerEvent.StartSession("session-${random.nextInt()}", steps.toList(), PRE_ALERT, longStepsOnly = false)

        private fun randomQuick() = TimerEvent.StartQuickTimer(
            durationSeconds = random.nextInt(15, 120),
            globalPreAlertSeconds = 10,
            preAlertSeconds = randomPreAlert(),
        )

        private fun randomPreAlert(): Int? = when (random.nextInt(3)) {
            0 -> null
            1 -> 5
            else -> random.nextInt(1, 7) * 5
        }
    }

    private val TimerState.rings: Boolean get() = this is TimerState.Alerting && !held

    private val userEvents = listOf(
        TimerEvent.Start, TimerEvent.Start, TimerEvent.Pause, TimerEvent.Resume, TimerEvent.Poured,
        TimerEvent.Poured, TimerEvent.Cancel, TimerEvent.EndStepNow, TimerEvent.SkipStep, TimerEvent.AddStep,
        TimerEvent.FinishSession, TimerEvent.Discard,
    )

    private companion object {
        const val RUNS = 300
        const val EVENTS_PER_RUN = 120
        const val RING_MS = 3_450L
        const val ALERT_CYCLE_MS = 15_000L

        /** The v0.3.0 pair: one brew session and the quick timer. */
        val PAIR = listOf(TimerSlot.BREW, TimerSlot.QUICK)
    }
}
