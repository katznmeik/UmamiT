package com.umamit.core.timer.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The timer's notification per phase (04 §8.2, §8.3, §12 Robolectric; TMR-06, TMR-14, TMR-18,
 * TMR-41): channel, ID, title, text, actions, promotion request, ProgressStyle segments, the
 * content and full-screen intents to SCR-05 and the delete intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerStateNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val states = NotificationStates(clock)
    private val notifications = TimerStateNotifications(context)

    private fun build(state: TimerUiState.Active, fullScreen: Boolean = false): Notification =
        notifications.build(state, fullScreen, clock.reading())

    // --- content per phase (04 §8.2) ---

    @Test
    fun `ready shows the step, its time and the kettle, with Start`() {
        val notification = build(states.ready())

        assertThat(notifications.idFor(states.ready())).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
        assertThat(notification.title()).isEqualTo("Gyokuro · Aufguss 2 von 3")
        assertThat(notification.text()).isEqualTo("Bereit · 1:00 · Wasserkocher auf 60 °C")
        assertThat(notification.actionTitles()).containsExactly("Start")
        assertThat(notification.shortCriticalText).isEqualTo("60 °C")
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
    }

    @Test
    fun `running counts down in the chip towards the deadline, with Pause`() {
        val state = states.running(remainingMs = 45_000)
        val notification = build(state)

        assertThat(notification.title()).isEqualTo("Gyokuro · Aufguss 2 von 3")
        assertThat(notification.text()).isEqualTo("Zieht bei 60 °C")
        assertThat(notification.actionTitles()).containsExactly("Pause")
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(notification.`when`).isEqualTo(state.deadlineWallMs)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    @Test
    fun `after the pre-alert the text says almost done`() {
        assertThat(build(states.running(remainingMs = 8_000, preAlertPlayed = true)).text())
            .isEqualTo("Gleich fertig …")
    }

    @Test
    fun `paused shows the time left, with Weiter`() {
        val notification = build(states.paused(remainingMs = 11_200))

        assertThat(notification.text()).isEqualTo("Pausiert · noch 0:12")
        assertThat(notification.actionTitles()).containsExactly("Weiter")
        assertThat(notification.shortCriticalText).isEqualTo("Pause")
    }

    @Test
    fun `finished asks to end the session`() {
        val notification = build(states.finished())

        assertThat(notification.title()).isEqualTo("Gyokuro · Alle Aufgüsse fertig")
        assertThat(notification.text()).isEqualTo("Tippen zum Beenden")
        assertThat(notification.actionTitles()).containsExactly("Sitzung beenden")
        assertThat(notification.shortCriticalText).isEqualTo("Fertig")
    }

    @Test
    fun `a finish that would discard the session is not offered without asking`() {
        // Sitzung beenden would discard it (TMR-12 AC1): only the app may ask first.
        val notification = build(states.finished().copy(finishWouldDiscard = true))

        assertThat(notification.actionTitles()).isEmpty()
    }

    @Test
    fun `a rinse and a whisk step are named by their kind`() {
        val rinse = states.ready().copy(stepIndex = 0)
        val whisk = states.ready().copy(steps = states.ready().steps.map { it.copy(kind = StepKind.WHISK) })

        assertThat(build(rinse).title()).isEqualTo("Gyokuro · Spülaufguss 1 von 3")
        assertThat(build(whisk).title()).isEqualTo("Gyokuro · Aufschlagen 2 von 3")
    }

    @Test
    fun `a long step says when it is done, on the quiet notification without the service`() {
        // 8 h cold brew, 2 h 30 min left at 10:00 in Berlin (TMR-18).
        val state = states.longStep(remainingMs = 150 * 60_000L)
        val notification = build(state)

        assertThat(notifications.idFor(state)).isEqualTo(TimerNotifications.ID_QUIET)
        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
        assertThat(notification.text()).isEqualTo("Fertig um 12:30")
        assertThat(notification.actionTitles()).containsExactly("Pause")
        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isTrue()
    }

    @Test
    fun `a demoted session is a quiet regular notification with Start and Sitzung beenden`() {
        // TMR-14: after 30 min without the service, a regular ongoing notification.
        val ready = states.ready().copy(demoted = true)
        val paused = states.paused().copy(demoted = true)

        assertThat(notifications.idFor(ready)).isEqualTo(TimerNotifications.ID_QUIET)
        val notification = build(ready)
        assertThat(notification.actionTitles()).containsExactly("Start", "Sitzung beenden").inOrder()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isFalse()
        assertThat(build(paused).actionTitles()).containsExactly("Weiter", "Sitzung beenden").inOrder()
    }

    // --- the alert (04 §8.2, D-044) ---

    @Test
    fun `the alert is notification 2 on steep_finished with Abgegossen`() {
        val notification = build(states.alerting(), fullScreen = true)

        assertThat(notifications.idFor(states.alerting())).isEqualTo(TimerNotifications.ID_ALERT)
        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(notification.title()).isEqualTo("Jetzt abgießen!")
        assertThat(notification.text()).isEqualTo("Gyokuro · Aufguss 2")
        assertThat(notification.actionTitles()).containsExactly("Abgegossen")
        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isFalse()
        assertThat(notification.deleteIntent).isNull()
    }

    @Test
    fun `the alert of a rinse says Weggegossen`() {
        val rinse = states.alerting().copy(stepIndex = 0)

        assertThat(build(rinse, fullScreen = true).actionTitles()).containsExactly("Weggegossen")
        assertThat(build(rinse, fullScreen = true).text()).isEqualTo("Gyokuro · Spülaufguss 1")
    }

    @Test
    fun `the full-screen intent opens the timer screen, only when the alert starts`() {
        val starting = build(states.alerting(), fullScreen = true)
        val shownAgain = build(states.alerting(), fullScreen = false)

        val target = shadowOf(starting.fullScreenIntent).savedIntent
        assertThat(target.action).isEqualTo(TimerScreenIntents.ACTION_OPEN_TIMER)
        assertThat(target.`package`).isEqualTo(context.packageName)
        assertThat(shownAgain.fullScreenIntent).isNull()
        // Shown again (an update of the same alert) it must not take over the screen once more.
        assertThat(shownAgain.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    }

    @Test
    fun `every timer notification opens the timer screen`() {
        listOf(states.ready(), states.running(), states.paused(), states.alerting(), states.finished()).forEach {
            val target = shadowOf(build(it).contentIntent).savedIntent
            assertThat(target.action).isEqualTo(TimerScreenIntents.ACTION_OPEN_TIMER)
        }
    }

    // --- quick timer (04 §10) ---

    @Test
    fun `the quick timer is called Schnelltimer and can be cancelled`() {
        val ready = build(states.quick(TimerPhase.READY))
        val running = build(states.quick(TimerPhase.RUNNING))
        val paused = build(states.quick(TimerPhase.PAUSED))
        val alerting = build(states.quick(TimerPhase.ALERTING), fullScreen = true)

        assertThat(ready.title()).isEqualTo("Schnelltimer")
        assertThat(ready.text()).isEqualTo("Bereit · 1:30")
        assertThat(ready.actionTitles()).containsExactly("Start", "Abbrechen").inOrder()
        assertThat(running.text()).isEqualTo("Zieht …")
        assertThat(running.actionTitles()).containsExactly("Pause", "Abbrechen").inOrder()
        assertThat(paused.actionTitles()).containsExactly("Weiter", "Abbrechen").inOrder()
        assertThat(alerting.text()).isEqualTo("Schnelltimer")
        assertThat(alerting.actionTitles()).containsExactly("Fertig")
    }

    // --- actions reach the controller (TMR-41) ---

    @Test
    fun `each action sends its event to the action receiver`() {
        val expected = listOf(
            states.ready() to TimerEvent.Start,
            states.running() to TimerEvent.Pause,
            states.paused() to TimerEvent.Resume,
            states.alerting() to TimerEvent.Poured,
            states.finished() to TimerEvent.FinishSession,
            states.quick(TimerPhase.RUNNING) to TimerEvent.Pause,
        )
        expected.forEach { (state, event) ->
            val intent = actionIntents(build(state, fullScreen = state.alerting)).first()
            assertThat(TimerActionReceiver.eventOf(intent)).isEqualTo(event)
        }
        val cancel = actionIntents(build(states.quick(TimerPhase.READY)))[1]
        assertThat(TimerActionReceiver.eventOf(cancel)).isEqualTo(TimerEvent.Cancel)
        val end = actionIntents(build(states.ready().copy(demoted = true)))[1]
        assertThat(TimerActionReceiver.eventOf(end)).isEqualTo(TimerEvent.FinishSession)
    }

    // --- Live Update (04 §8.3) ---

    @Test
    fun `every Live Update requests promotion and is promotable`() {
        listOf(states.ready(), states.running(), states.paused(), states.finished()).forEach {
            val notification = build(it)
            assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isTrue()
            assertThat(notification.hasPromotableCharacteristics()).isTrue()
        }
    }

    @Test
    fun `the steps are ProgressStyle segments with the pre-alert point and the session's progress`() {
        // Rinse 4 s (done), 60 s with 45 s left, 30 s; in tenths of a second. The rinse is
        // shorter than the minimum length (a 20th of the 94 s plan) and is drawn at 4,7 s.
        val style = progressStyle(build(states.running(remainingMs = 45_000)))

        assertThat(style.progressSegments.map { it.length }).containsExactly(47, 600, 300).inOrder()
        assertThat(style.progressSegments[0].color).isNotEqualTo(style.progressSegments[1].color)
        // The pre-alert 10 s before the end of step 2.
        assertThat(style.progressPoints.map { it.position }).containsExactly(47 + 500)
        // The rinse, plus 15 s of step 2.
        assertThat(style.progress).isEqualTo(47 + 150)
    }

    @Test
    fun `a finished session's progress is full`() {
        val style = progressStyle(build(states.finished()))

        assertThat(style.progress).isEqualTo(947)
        assertThat(style.progressPoints).isEmpty()
    }

    @Test
    fun `at most 30 segments`() {
        val many = states.ready().copy(steps = List(40) { states.ready().steps[1] })

        assertThat(progressStyle(build(many)).progressSegments).hasSize(30)
    }

    @Test
    fun `a dismissed Live Update reports itself with its state`() {
        val notification = build(states.running())

        val dismissal = shadowOf(notification.deleteIntent).savedIntent
        assertThat(TimerActionReceiver.dismissedKeyOf(dismissal)).isEqualTo(TimerNotificationKey.of(states.running()))
    }

    @Test
    @Config(sdk = [35])
    fun `on Android 15 the Live Update is a normal ongoing notification`() {
        val notification = build(states.running())

        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isFalse()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
    }

    // --- „Sitzung gespeichert" (TMR-14) ---

    @Test
    fun `the saved session notification names the tea on the sessions channel`() {
        val notification = notifications.sessionSaved("Gyokuro")

        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_SESSIONS)
        assertThat(notification.title()).isEqualTo("Sitzung gespeichert")
        assertThat(notification.text()).isEqualTo("Gyokuro · im Journal")
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(notifications.sessionSaved(null).text()).isEqualTo("Im Journal")
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.actionTitles() = (actions ?: emptyArray()).map { it.title.toString() }

    private fun actionIntents(notification: Notification): List<Intent> =
        notification.actions.map { shadowOf(it.actionIntent).savedIntent }

    private fun progressStyle(notification: Notification): Notification.ProgressStyle =
        Notification.Builder.recoverBuilder(context, notification).style as Notification.ProgressStyle
}
