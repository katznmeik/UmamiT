package com.umamit.app.timer

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.app.MainActivity
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.notification.TimerScreenIntents
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notifications' way to the timer (#47, D-064): the intent names the app's activity, and
 * a request the navigation has not handled yet survives the activity's recreation, so a
 * full-screen intent that came while the phone slept still shows the timer after a
 * configuration change (dark mode, rotation, a fold).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerScreenRequestsTest {
    private var next = 0L
    private fun requests() = TimerScreenRequests(newToken = { ++next })

    @Test
    fun `the timer intent opens the app's activity without an intent filter`() {
        val app: Application = ApplicationProvider.getApplicationContext()

        val intent = TimerScreenIntents.openTimer(app)

        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(TimerScreenIntents.isOpenTimer(intent)).isTrue()
    }

    @Test
    fun `a request not handled yet survives the recreation`() {
        val before = requests()
        before.request(openTimer())
        val saved = Bundle().also(before::save)

        val after = requests()
        assertThat(after.restore(saved, null)).isTrue()

        assertThat(after.request.value.token).isEqualTo(before.request.value.token)
        assertThat(after.pending).isEqualTo(before.request.value.token)
    }

    @Test
    fun `a handled request is not opened again after the recreation`() {
        val before = requests()
        before.request(openTimer())
        before.handled(before.request.value.token)
        val saved = Bundle().also(before::save)

        val after = requests()

        assertThat(after.restore(saved, null)).isFalse()
        assertThat(after.request.value.token).isEqualTo(0L)
    }

    @Test
    fun `handling an older request leaves a newer one pending`() {
        val requests = requests()
        requests.request(openTimer())
        val older = requests.request.value.token
        requests.request(openTimer())

        requests.handled(older)

        assertThat(requests.pending).isEqualTo(requests.request.value.token)
    }

    @Test
    fun `a stopped activity relaunched with its older state still opens the timer (R2-1)`() {
        // Saved at the stop behind the keyguard, before any request.
        val before = requests()
        val savedAtStop = Bundle().also(before::save)
        // The full-screen intent reaches the stopped activity; Android relaunches it for a
        // configuration change with the state of the stop and this intent, without saving again.
        val intent = openTimer()
        before.request(intent)

        val after = requests()

        assertThat(after.restore(savedAtStop, intent)).isTrue()
        assertThat(after.request.value.token).isEqualTo(before.request.value.token)
    }

    @Test
    fun `the intent's request is not opened again once it was handled`() {
        val before = requests()
        val intent = openTimer()
        before.request(intent)
        before.handled(before.request.value.token)
        val saved = Bundle().also(before::save)

        assertThat(requests().restore(saved, intent)).isFalse()
    }

    @Test
    fun `a request names the notification's timer, also after the recreation (04 §8_2)`() {
        val before = requests()
        val intent = openTimer(TimerSlot.QUICK)
        before.request(intent)
        val saved = Bundle().also(before::save)

        val restored = requests().also { it.restore(saved, null) }
        val relaunched = requests().also { it.restore(Bundle(), intent) }

        assertThat(before.request.value.slot).isEqualTo(TimerSlot.QUICK)
        assertThat(restored.request.value).isEqualTo(before.request.value)
        assertThat(relaunched.request.value).isEqualTo(before.request.value)
        // A notice that names no timer opens the one in front.
        val front = requests().also { it.request(openTimer()) }
        assertThat(front.request.value.slot).isNull()
    }

    private fun openTimer(slot: TimerSlot? = null) =
        TimerScreenIntents.openTimer(ApplicationProvider.getApplicationContext<Application>(), slot)
}
