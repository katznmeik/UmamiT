package com.umamit.core.data

import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogNotifications
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.reminder.BackupReminderNotifications
import com.umamit.core.data.reminder.LowStockReminderNotifications
import com.umamit.core.model.LowStockTea
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The catalog's, the low-stock and the backup reminders' notifications and channels follow the app's language, not
 * the process's (GEN-13 AC1; D-136, „A process without an activity"): a process with no activity runs in the phone's
 * language (here English, or German where a test says so), while Android holds the app's language. Synthetic teas
 * (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "+en-rUS")
class AppLanguageNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val teas = listOf(LowStockTea("t2", "Hisui", 0.0), LowStockTea("t1", "Tanabata", 12.5))

    @After
    fun tearDown() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun `with German for the app the low-stock reminder is German in words and numbers in an English process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")

        val reminder = LowStockReminderNotifications.notification(context, teas)

        assertThat(reminder.title()).isEqualTo("2 Tees werden knapp")
        assertThat(reminder.text()).isEqualTo("Hisui · leer, Tanabata · 12,5\u00A0g")
    }

    @Test
    fun `with German for the app the channels are German in an English process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")

        CatalogNotifications.ensureChannel(context)
        LowStockReminderNotifications.ensureChannel(context)
        BackupReminderNotifications.ensureChannel(context)

        assertThat(channelName(CatalogNotifications.CHANNEL_ID)).isEqualTo("Katalog")
        assertThat(channelName(LowStockReminderNotifications.CHANNEL_ID)).isEqualTo("Vorrat")
        assertThat(channelName(BackupReminderNotifications.CHANNEL_ID)).isEqualTo("Erinnerungen")
        assertThat(manager.getNotificationChannel(LowStockReminderNotifications.CHANNEL_ID).description.toString())
            .isEqualTo("Erinnerung an knappe und leere Tees")
    }

    @Test
    fun `with German for the app the catalog's and the backup's notifications are German in an English process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")

        val result =
            checkNotNull(CatalogNotifications.result(context, CatalogRefreshOutcome.Success(teas = 3, newTeas = 1)))
        assertThat(result.title()).isEqualTo("Katalog geladen")
        assertThat(result.text()).isEqualTo("3\u00A0Tees\u00A0· 1\u00A0neu")
        assertThat(CatalogNotifications.progress(context, CatalogRefreshProgress(0, 0, 0)).title())
            .isEqualTo("Katalog wird geladen")
        assertThat(BackupReminderNotifications.notification(context, sessions = 23, neverSaved = false).text())
            .isEqualTo("23 neue Sessions seit der letzten Sicherung.")
    }

    @Test
    @Config(qualifiers = "+de-rDE")
    fun `with English for the app the reminders are English in words and numbers in a German process`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("en")

        val reminder = LowStockReminderNotifications.notification(context, teas)
        assertThat(reminder.title()).isEqualTo("2 teas are running low")
        assertThat(reminder.text()).isEqualTo("Hisui · empty, Tanabata · 12.5\u00A0g")

        LowStockReminderNotifications.ensureChannel(context)
        assertThat(channelName(LowStockReminderNotifications.CHANNEL_ID)).isEqualTo("Stock")
    }

    @Test
    fun `a text built after a language change follows the new language`() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("de")
        assertThat(LowStockReminderNotifications.notification(context, teas).title()).isEqualTo("2 Tees werden knapp")

        localeManager.applicationLocales = LocaleList.forLanguageTags("en")
        assertThat(LowStockReminderNotifications.notification(context, teas).title())
            .isEqualTo("2 teas are running low")

        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertThat(LowStockReminderNotifications.notification(context, teas).text())
            .isEqualTo("Hisui · empty, Tanabata · 12.5 g")
    }

    @Test
    fun `with System the process's language holds`() {
        val reminder = LowStockReminderNotifications.notification(context, teas)
        assertThat(reminder.text()).isEqualTo("Hisui · empty, Tanabata · 12.5\u00A0g")

        val result =
            checkNotNull(CatalogNotifications.result(context, CatalogRefreshOutcome.Success(teas = 3, newTeas = 1)))
        assertThat(result.title()).isEqualTo("Catalog loaded")
    }

    private fun channelName(id: String) = manager.getNotificationChannel(id).name.toString()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
