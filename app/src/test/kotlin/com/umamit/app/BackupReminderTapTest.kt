package com.umamit.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.reminder.OpenBackupIntents
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.settings.SettingsTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap on the backup reminder (BAK-06, 03 §2.4, D-130 Q11): its intent names the app's launcher activity with
 * `OPEN_BACKUP`, and the real root and shell turn it into *Daten & Sicherung* (SCR-26) in Start's stack, below it
 * *Einstellungen*, whatever was shown. An alert wins: the tap waits until it has ended (rule 8). Before the first-start
 * setup is done the tap is let go (ONB-03).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BackupReminderTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val requests = BackupRequests(newToken = { ++tokens })
    private var tokens = 0L
    private val alertKey = MutableStateFlow<String?>(null)
    private val completed = MutableStateFlow<Boolean?>(true)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<com.umamit.core.designsystem.component.ActiveBrew?> = MutableStateFlow(null)
        override val progress: () -> Float = { 0f }
    }

    private fun show() {
        val settings = SettingsScreens(
            screen = testSettingsScreens().screen,
            dataBackup = { Text("SICHERUNG") },
        )
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    onboardingCompleted = completed,
                    onboardingScreen = { Text("EINRICHTUNG") },
                    backupRequest = requests.token,
                    onConsumeBackupRequest = requests::handled,
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    settings = settings,
                    startScreen = { Text("START") },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The notification's content intent, as MainActivity gets it in `onCreate` or `onNewIntent`. */
    private fun tapNotification() {
        compose.runOnUiThread { assertThat(requests.offer(OpenBackupIntents.openBackup(compose.activity))).isTrue() }
        compose.waitForIdle()
    }

    private fun backupShown() {
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithText("SICHERUNG").assertIsDisplayed()
    }

    @Test
    fun `the intent names the launcher activity without an intent filter`() {
        val intent = OpenBackupIntents.openBackup(compose.activity)

        assertThat(intent.component).isEqualTo(ComponentName(compose.activity, MainActivity::class.java))
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        val byAction = Intent(OpenBackupIntents.ACTION_OPEN_BACKUP).setPackage(compose.activity.packageName)
        assertThat(compose.activity.packageManager.queryIntentActivities(byAction, PackageManager.MATCH_ALL))
            .isEmpty()
    }

    @Test
    fun `a tap opens SCR-26 from Start`() {
        show()

        tapNotification()

        backupShown()
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun `a tap from another tab opens SCR-26 in Start`() {
        show()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()

        tapNotification()

        backupShown()
    }

    @Test
    fun `leaving for another tab closes the settings with SCR-26`() {
        show()
        tapNotification()

        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(tabTag(Tab.Start)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("SICHERUNG").assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.DATA_BACKUP).assertDoesNotExist()
        compose.onNodeWithText("START").assertIsDisplayed()
    }

    @Test
    fun `a tap while SCR-26 is open leaves it as it is`() {
        show()
        tapNotification()
        backupShown()

        tapNotification()

        backupShown()
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun `during an alert the tap waits until it has ended`() {
        show()
        alertKey.value = "A"
        compose.waitForIdle()

        tapNotification()

        compose.onNodeWithText("SICHERUNG").assertDoesNotExist()
        assertThat(requests.pending).isNotEqualTo(0L)

        alertKey.value = null
        compose.waitForIdle()

        backupShown()
    }

    @Test
    fun `before the setup is finished the tap is let go`() {
        completed.value = false
        show()

        tapNotification()

        compose.onNodeWithText("EINRICHTUNG").assertIsDisplayed()
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun `only a fresh OPEN_BACKUP intent is a request and a pending one survives recreation`() {
        val tap = OpenBackupIntents.openBackup(compose.activity)

        assertThat(requests.offer(Intent(Intent.ACTION_MAIN))).isFalse()
        assertThat(requests.offer(null)).isFalse()
        assertThat(requests.offer(Intent(tap).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))).isFalse()
        assertThat(requests.offer(tap)).isTrue()

        val recreated = BackupRequests()
        recreated.restore(Bundle().also(requests::save))
        assertThat(recreated.pending).isEqualTo(requests.pending)
    }
}
