package com.umamit.core.timer.notification

import android.app.Application
import android.app.Notification
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerInputs
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
import com.umamit.core.timer.controller.rinse
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.controller.state
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The app as the action receiver finds the controller (D-057): here the controller of the harness. */
class ActionTestHost :
    Application(),
    TimerHost {
    var inputs: TimerInputs? = null

    override val timerInputs: TimerInputs get() = checkNotNull(inputs) { "test setup: no controller" }
}

/**
 * Action round trips (TMR-07, TMR-41; spec #40 seam 3): each action of the notification the
 * controller's state shows is tapped, reaches the real controller as a user event from the
 * notification, and moves the session on, as the same tap in the app would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ActionTestHost::class)
class NotificationActionsTest {
    private val app: ActionTestHost = ApplicationProvider.getApplicationContext()
    private val harness = ControllerHarness().also {
        app.inputs = it.controller
        runBlocking { it.teaWithStock() }
    }
    private val notifications = TimerStateNotifications(app)

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a whole session is brewed from the notification`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(rinse(10), infusion(30)))

        tap("Start", expect = TimerPhase.RUNNING)
        tap("Pause", expect = TimerPhase.PAUSED)
        tap("Weiter", expect = TimerPhase.RUNNING)
        harness.advanceSeconds(10)
        tap("Weggegossen", expect = TimerPhase.READY)
        assertThat(harness.active.stepIndex).isEqualTo(1)
        tap("Start", expect = TimerPhase.RUNNING)
        harness.advanceSeconds(30)
        tap("Abgegossen", expect = TimerPhase.FINISHED)
        tap("Sitzung beenden", expect = null)

        val sources = harness.diagnostics.entriesOf<TimerDiagnostic.EventHandled>()
            .filter { it.event !is TimerEvent.StartSession && it.source != EventSource.WAKE_UP }
            .map { it.source }
            .distinct()
        assertThat(sources).containsExactly(EventSource.NOTIFICATION)
    }

    @Test
    fun `the quick timer is finished and cancelled from the notification`() = runBlocking<Unit> {
        harness.controller.startQuickTimer(60)
        tap("Start", expect = TimerPhase.RUNNING)
        harness.advanceSeconds(60)
        tap("Fertig", expect = null)

        harness.controller.startQuickTimer(60)
        tap("Abbrechen", expect = null)
    }

    @Test
    fun `a demoted session is ended from its quiet notification`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(30), infusion(30)))
        tap("Start", expect = TimerPhase.RUNNING)
        harness.advanceSeconds(30)
        tap("Abgegossen", expect = TimerPhase.READY)
        // 30 min untouched: the session goes quiet (TMR-14).
        harness.advanceSeconds(30 * 60)
        assertThat(harness.active.demoted).isTrue()

        tap("Sitzung beenden", expect = null)
    }

    @Test
    fun `the quick timer's notification 5 beside a brew acts on the quick timer only`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(120)))
        harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
        harness.controller.startQuickTimer(300, run = true)
        // The brew ends first: it is the Live Update, the quick timer notification 5 (04 §8.3, D-098).
        assertThat(harness.notifier.other?.slot).isEqualTo(TimerSlot.QUICK)
        val brew = harness.controller.state(TimerSlot.BREW).value

        tapOther(TimerSlot.QUICK, "Pause", expect = TimerPhase.PAUSED)
        assertThat(harness.controller.state(TimerSlot.BREW).value).isEqualTo(brew)
        tapOther(TimerSlot.QUICK, "Weiter", expect = TimerPhase.RUNNING)
        tapOther(TimerSlot.QUICK, "Abbrechen", expect = null)

        assertThat(harness.controller.state(TimerSlot.BREW).value).isEqualTo(brew)
    }

    @Test
    fun `an action of v0,2,2 without a slot reaches the brew slot`() {
        val legacy = android.content.Intent(app, TimerActionReceiver::class.java)
            .setAction("com.umamit.core.timer.action.NOTIFICATION.PAUSE")

        assertThat(TimerActionReceiver.eventOf(legacy)).isEqualTo(TimerEvent.Pause)
        assertThat(TimerActionReceiver.slotOf(legacy)).isEqualTo(TimerSlot.BREW)
        val quick = TimerActionReceiver.intent(app, TimerEvent.Pause, TimerSlot.QUICK)
        assertThat(TimerActionReceiver.eventOf(quick)).isEqualTo(TimerEvent.Pause)
        assertThat(TimerActionReceiver.slotOf(quick)).isEqualTo(TimerSlot.QUICK)
    }

    /** Taps the action [label] of [slot]'s notification 5 and waits for its timer's [expect] (null: idle). */
    private fun tapOther(slot: TimerSlot, label: String, expect: TimerPhase?) {
        val state = harness.controller.state(slot).value as TimerUiState.Active
        val action = notifications.other(state, harness.clock.reading()).actions
            .firstOrNull { it.title.toString() == label }
        assertWithMessage("action $label of notification 5 in ${state.phase}").that(action).isNotNull()
        app.sendBroadcast(shadowOf(action!!.actionIntent).savedIntent)
        val phaseOf = { (harness.controller.state(slot).value as? TimerUiState.Active)?.phase }
        val start = TimeSource.Monotonic.markNow()
        while (phaseOf() != expect && start.elapsedNow() < TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        assertWithMessage("phase after $label").that(phaseOf()).isEqualTo(expect)
    }

    /** Taps the action [label] of the notification of the controller's state and waits for [expect] (null: idle). */
    private fun tap(label: String, expect: TimerPhase?) {
        val state = harness.active
        val notification: Notification = notifications.build(
            state,
            fullScreen = state.alerting,
            now = harness.clock.reading(),
        )
        val action = notification.actions.firstOrNull { it.title.toString() == label }
        assertWithMessage("action $label in ${state.phase}").that(action).isNotNull()
        app.sendBroadcast(shadowOf(action!!.actionIntent).savedIntent)
        val start = TimeSource.Monotonic.markNow()
        while (phase() != expect && start.elapsedNow() < TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        assertWithMessage("phase after $label").that(phase()).isEqualTo(expect)
    }

    private fun phase(): TimerPhase? = (harness.ui as? TimerUiState.Active)?.phase

    private companion object {
        val TIMEOUT = 5.seconds
        const val POLL_MS = 10L
    }
}
