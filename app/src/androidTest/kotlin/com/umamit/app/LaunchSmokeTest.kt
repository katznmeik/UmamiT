package com.umamit.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch smoke test (M1-02, #12; plan M1-timer.md §3.1 seam 2): the app starts
 * its launcher activity and shows the German placeholder screen. Runs only in
 * the CI emulator job (API 35 and 36); the cloud container has no KVM.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsGermanPlaceholder() {
        // Read the text from the app's resources, so the test follows strings.xml.
        val welcome = composeRule.activity.getString(R.string.placeholder_welcome)

        composeRule.onNodeWithText(welcome).assertDoesNotExist()
    }
}
