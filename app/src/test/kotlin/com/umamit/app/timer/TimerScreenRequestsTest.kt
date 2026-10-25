package com.umamit.app.timer

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.app.MainActivity
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

        assertThat(after.token.value).isEqualTo(before.token.value)
        assertThat(after.pending).isEqualTo(before.token.value)
    }

    @Test
    fun `a handled request is not opened again after the recreation`() {
        val before = requests()
        before.request(openTimer())
        before.handled(before.token.value)
        val saved = Bundle().also(before::save)

        val after = requests()

        assertThat(after.restore(saved, null)).isFalse()
        assertThat(after.token.value).isEqualTo(0L)
    }

    @Test
    fun `handling an older request leaves a newer one pending`() {
        val requests = requests()
        requests.request(openTimer())
        val older = requests.token.value
        requests.request(openTimer())

        requests.handled(older)

        assertThat(requests.pending).isEqualTo(requests.token.value)
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
        assertThat(after.token.value).isEqualTo(before.token.value)
    }

    @Test
    fun `the intent's request is not opened again once it was handled`() {
        val before = requests()
        val intent = openTimer()
        before.request(intent)
        before.handled(before.token.value)
        val saved = Bundle().also(before::save)

        assertThat(requests().restore(saved, intent)).isFalse()
    }

    private fun openTimer() = TimerScreenIntents.openTimer(ApplicationProvider.getApplicationContext<Application>())
}
