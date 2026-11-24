package com.umamit.core.timer.notification

import android.app.LocaleManager
import android.app.NotificationManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.TimerNotifications
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The timer's channel names and descriptions follow the app's language, not the process's (GEN-13 AC1; D-136 item
 * 15): [TimerNotifications.ensureChannels] runs from the service and its receivers, often in a process with no
 * activity, which runs in the phone's language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "+en-rUS")
class AppLanguageChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun `with German for the app the channels are German in an English process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")

        TimerNotifications.ensureChannels(context)

        assertThat(names()).isEqualTo(
            mapOf(
                "brew_timer" to "Aufguss-Timer · Der laufende Aufguss als Live-Update",
                "steep_finished" to "Aufguss fertig · Alarm am Ende eines Aufgusses",
                "sessions" to "Sessions · Automatisch gespeicherte Sessions",
                "timer_other" to "Weiterer Timer · Der zweite laufende Timer, still neben dem Aufguss",
            ),
        )
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `with English for the app the channels are English in a German process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("en")

        TimerNotifications.ensureChannels(context)

        assertThat(names()).isEqualTo(
            mapOf(
                "brew_timer" to "Infusion timer · The running infusion as a Live Update",
                "steep_finished" to "Infusion done · Alarm at the end of an infusion",
                "sessions" to "Sessions · Automatically saved sessions",
                "timer_other" to "Second timer · The second running timer, silent next to the infusion",
            ),
        )
    }

    @Test
    fun `a change of the app language renames the channels at the next ensureChannels`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")
        TimerNotifications.ensureChannels(context)
        assertThat(names()["steep_finished"]).isEqualTo("Aufguss fertig · Alarm am Ende eines Aufgusses")

        localeManager.applicationLocales = LocaleList.forLanguageTags("en")
        TimerNotifications.ensureChannels(context)

        assertThat(names()["steep_finished"]).isEqualTo("Infusion done · Alarm at the end of an infusion")
    }

    @Test
    fun `with System the process's language holds`() {
        TimerNotifications.ensureChannels(context)

        assertThat(names()["brew_timer"]).isEqualTo("Infusion timer · The running infusion as a Live Update")
        assertThat(names()["timer_other"])
            .isEqualTo("Second timer · The second running timer, silent next to the infusion")
    }

    private fun names(): Map<String, String> =
        manager.notificationChannels.associate { it.id to "${it.name} · ${it.description}" }
}
