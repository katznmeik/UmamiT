package com.umamit.core.timer.notification

import android.app.LocaleManager
import android.app.Notification
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.testing.FakeClock
import com.umamit.core.timer.TimerStateNotifications
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer's notifications follow the app's language, not the process's (GEN-10, SET-35; D-136 item 15): a process
 * with no activity runs in the phone's language (here English), while Android holds German for the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "+en-rUS")
class AppLanguageNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val clock = FakeClock()
    private val states = NotificationStates(clock)

    @After
    fun tearDown() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun `with German for the app the alert, its action and the Live Update are German in an English process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")
        val notifications = TimerStateNotifications(context)

        val alert = notifications.build(states.alerting(), fullScreen = true, clock.reading())
        assertThat(alert.text()).isEqualTo("Gyokuro · Aufguss 2")
        assertThat(alert.actionTitles()).containsExactly("Abgegossen")
        assertThat(notifications.build(states.paused(), fullScreen = false, clock.reading()).text())
            .startsWith("Pausiert")
    }

    @Test
    fun `a change of the app language shows in the next post`() {
        val notifications = TimerStateNotifications(context)
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")
        assertThat(notifications.build(states.alerting(), fullScreen = true, clock.reading()).text())
            .isEqualTo("Gyokuro · Aufguss 2")

        localeManager.applicationLocales = LocaleList.forLanguageTags("en")
        assertThat(notifications.build(states.alerting(), fullScreen = true, clock.reading()).text())
            .isEqualTo("Gyokuro · Infusion 2")
    }

    @Test
    fun `with System the process's language holds`() {
        val alert = TimerStateNotifications(context).build(states.alerting(), fullScreen = true, clock.reading())

        assertThat(alert.text()).isEqualTo("Gyokuro · Infusion 2")
        assertThat(alert.actionTitles()).containsExactly("Poured")
    }

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.actionTitles() = (actions ?: emptyArray()).map { it.title.toString() }
}
