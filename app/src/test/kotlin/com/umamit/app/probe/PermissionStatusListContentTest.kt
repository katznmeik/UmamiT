package com.umamit.app.probe

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.umamit.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Rendering of the stateless status list (M1-03a, #14): German labels, ✓/✗ states, taps. */
// SDK 35: Robolectric's SDK 36 sandbox needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`
// on JDK 21 (ApplicationSharedMemory), which the shared test configuration does not pass yet.
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class PermissionStatusListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun stateIs(id: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(id))

    private val mixedRows = listOf(
        PermissionStatusRow(PermissionStatusItem.Notifications, PermissionState.Granted),
        PermissionStatusRow(PermissionStatusItem.PromotedNotifications, PermissionState.Unavailable),
        PermissionStatusRow(PermissionStatusItem.FullScreenIntent, PermissionState.Denied),
        PermissionStatusRow(PermissionStatusItem.ExactAlarms, PermissionState.Granted),
        PermissionStatusRow(PermissionStatusItem.BatteryOptimization, PermissionState.Denied),
    )

    @Test
    fun rendersAllFiveRowsWithGermanLabelsAndStates() {
        composeRule.setContent { PermissionStatusListContent(rows = mixedRows, onRowClick = {}) }

        listOf(
            R.string.probe_status_notifications_label,
            R.string.probe_status_promoted_label,
            R.string.probe_status_full_screen_label,
            R.string.probe_status_exact_alarms_label,
            R.string.probe_status_battery_label,
        ).forEach { composeRule.onNodeWithText(string(it), useUnmergedTree = true).assertExists() }

        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.Notifications))
            .assert(stateIs(R.string.probe_status_state_granted))
        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.PromotedNotifications))
            .assert(stateIs(R.string.probe_status_state_unavailable))
        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.FullScreenIntent))
            .assert(stateIs(R.string.probe_status_state_denied))
        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.BatteryOptimization))
            .assert(stateIs(R.string.probe_status_state_denied))
    }

    @Test
    fun showsCheckAndCrossSymbols() {
        composeRule.setContent { PermissionStatusListContent(rows = mixedRows, onRowClick = {}) }

        composeRule.onAllNodesWithText(string(R.string.probe_status_symbol_granted), useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText(string(R.string.probe_status_symbol_denied), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test
    fun tappingARowReportsIt() {
        val clicked = mutableListOf<PermissionStatusRow>()
        composeRule.setContent { PermissionStatusListContent(rows = mixedRows, onRowClick = { clicked += it }) }

        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.FullScreenIntent))
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf(mixedRows[2]), clicked)
    }

    @Test
    fun unavailableRowIsNotClickable() {
        composeRule.setContent { PermissionStatusListContent(rows = mixedRows, onRowClick = {}) }

        composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.PromotedNotifications))
            .assertIsNotEnabled()
    }
}
