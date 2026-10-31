package com.umamit.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.reminder.OpenStartIntents
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap on the daily low-stock reminder (INV-11, 03 §2.4, D-098; ticket #130): its intent names the app's
 * launcher activity with `OPEN_START`, and the real root and shell turn it into Start at its root, whatever
 * was shown, the timer included (its session keeps running). An alert wins: the tap waits until it has
 * ended (rule 8). Before the first-start setup is done the tap is let go (ONB-03).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LowStockReminderTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val requests = StartRequests(newToken = { ++tokens })
    private var tokens = 0L
    private val alertKey = MutableStateFlow<String?>(null)
    private val completed = MutableStateFlow<Boolean?>(true)
    private val brew = MutableStateFlow<ActiveBrew?>(null)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    onboardingCompleted = completed,
                    onboardingScreen = { Text("EINRICHTUNG") },
                    startRequest = requests.token,
                    onConsumeStartRequest = requests::handled,
                    timerScreen = { _, _, _, _ -> Text("TIMER") },
                    settings = testSettingsScreens(),
                    startScreen = { Text("START") },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The notification's content intent, as MainActivity gets it in `onCreate` or `onNewIntent`. */
    private fun tapNotification() {
        compose.runOnUiThread { assertThat(requests.offer(OpenStartIntents.openStart(compose.activity))).isTrue() }
        compose.waitForIdle()
    }

    private fun startShown() {
        compose.onNodeWithTag(tabTag(Tab.Start)).assertIsSelected()
        compose.onNodeWithText("START").assertIsDisplayed()
    }

    @Test
    fun theIntentNamesTheLauncherActivityWithoutAnIntentFilter() {
        val intent = OpenStartIntents.openStart(compose.activity)
        val main = ComponentName(compose.activity, MainActivity::class.java)

        assertThat(intent.component).isEqualTo(main)
        assertThat(OpenStartIntents.LAUNCHER_ACTIVITY).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotEqualTo(0)
        // No intent filter answers the action: only the explicit intent reaches the app.
        val byAction = Intent(OpenStartIntents.ACTION_OPEN_START).setPackage(compose.activity.packageName)
        assertThat(compose.activity.packageManager.queryIntentActivities(byAction, PackageManager.MATCH_ALL))
            .isEmpty()
    }

    @Test
    fun aTapFromAnotherTabOpensStart() {
        show()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()

        tapNotification()

        startShown()
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun aTapOverTheTimerOpensStartAndTheSessionKeepsRunning() {
        brew.value = RUNNING
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        tapNotification()

        startShown()
        compose.onNodeWithText("TIMER").assertDoesNotExist()
        assertThat(brew.value).isEqualTo(RUNNING)
    }

    @Test
    fun duringAnAlertTheTapWaitsUntilItHasEnded() {
        brew.value = RUNNING.copy(alerting = true)
        alertKey.value = "A"
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        tapNotification()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(requests.pending).isNotEqualTo(0L)

        alertKey.value = null
        brew.value = RUNNING
        compose.waitForIdle()

        startShown()
    }

    @Test
    fun beforeTheSetupIsFinishedTheTapIsLetGo() {
        completed.value = false
        show()

        tapNotification()

        compose.onNodeWithText("EINRICHTUNG").assertIsDisplayed()
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun onlyAFreshOpenStartIntentIsARequest() {
        val tap = OpenStartIntents.openStart(compose.activity)

        assertThat(requests.offer(Intent(Intent.ACTION_MAIN))).isFalse()
        assertThat(requests.offer(null)).isFalse()
        // A task reopened from the recent apps brings its first intent again: no new request.
        assertThat(requests.offer(Intent(tap).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))).isFalse()
        assertThat(requests.pending).isEqualTo(0L)
        assertThat(requests.offer(tap)).isTrue()
        assertThat(requests.pending).isNotEqualTo(0L)
    }

    @Test
    fun aPendingTapSurvivesTheActivitysRecreation() {
        requests.offer(OpenStartIntents.openStart(compose.activity))
        val state = Bundle().also(requests::save)

        val recreated = StartRequests()
        recreated.restore(state)

        assertThat(recreated.pending).isEqualTo(requests.pending)
        recreated.handled(recreated.pending)
        assertThat(recreated.pending).isEqualTo(0L)
    }

    private companion object {
        val RUNNING =
            ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    }
}
