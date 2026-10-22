package com.umamit.core.timer

import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.controller.EventSource
import com.umamit.core.timer.engine.TimerEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The interim full-screen alert (D-057): *Abgegossen* stops the alert from the lock screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTimerHost::class)
class TimerAlertActivityTest {
    private val app: TestTimerHost = ApplicationProvider.getApplicationContext()

    @Test
    fun abgegossenSendsPouredToTheControllerAndCloses() {
        val activity = Robolectric.buildActivity(
            TimerAlertActivity::class.java,
            TimerAlertActivity.intent(app, "Hisui"),
        )
            .setup()
            .get()

        activity.findViewById<Button>(R.id.timer_alert_done).performClick()

        assertThat(app.events.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(TimerEvent.Poured to EventSource.APP)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun aSecondAlertShowsItsOwnTea() {
        val controller = Robolectric.buildActivity(
            TimerAlertActivity::class.java,
            TimerAlertActivity.intent(app, "Hisui"),
        )
            .setup()

        controller.newIntent(TimerAlertActivity.intent(app, "Schnelltimer"))

        val subtitle = controller.get().findViewById<TextView>(R.id.timer_alert_subtitle)
        assertThat(subtitle.text.toString()).isEqualTo("Schnelltimer")
        assertThat(controller.get().isFinishing).isFalse()
    }

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
