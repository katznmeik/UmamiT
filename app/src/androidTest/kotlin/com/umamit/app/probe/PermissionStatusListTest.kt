package com.umamit.app.probe

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The probe's status list on a real system (M1-03a, #14; runs in the CI
 * emulator job on API 35 and 36). States the emulator lets a test toggle
 * without killing the app are switched through shell commands, then the list's
 * lifecycle is paused and resumed: it must show the new state after the refresh.
 * The list runs under its own [LifecycleOwner] so the test does not push the
 * activity through STARTED: on the API 35 emulator that overlay activity could
 * fail to start within Espresso's 45 s timeout and leave the scenario without a
 * state (CI run 39787128455).
 *
 * - Full-screen intent: `appops set … USE_FULL_SCREEN_INTENT deny|allow`.
 * - Battery optimisation: `cmd deviceidle whitelist -|+…`.
 * - Notifications: granted with `pm grant` (revoking a runtime permission
 *   kills the app process, so the denied case is covered by unit tests).
 * - Exact alarms: always granted through `USE_EXACT_ALARM`.
 * - Promoted notifications: unavailable on API 35; a real ✓/✗ on API 36.
 */
@RunWith(AndroidJUnit4::class)
class PermissionStatusListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private lateinit var listLifecycle: ListLifecycleOwner

    @Before
    fun setUp() {
        shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        composeRule.runOnUiThread { listLifecycle = ListLifecycleOwner() }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides listLifecycle) { PermissionStatusList() }
        }
    }

    @After
    fun restoreDefaults() {
        shell("appops set $packageName USE_FULL_SCREEN_INTENT default")
        shell("cmd deviceidle whitelist -$packageName")
    }

    @Test
    fun allFiveRowsRenderWithTheirGermanLabels() {
        listOf(
            R.string.probe_status_notifications_label,
            R.string.probe_status_promoted_label,
            R.string.probe_status_full_screen_label,
            R.string.probe_status_exact_alarms_label,
            R.string.probe_status_battery_label,
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label), useUnmergedTree = true).assertExists()
        }
        PermissionStatusItem.entries.forEach { composeRule.onNodeWithTag(permissionStatusRowTag(it)).assertExists() }
    }

    @Test
    fun grantedNotificationsAndExactAlarmsShowAsGranted() {
        refreshUntilRowShows(PermissionStatusItem.Notifications, R.string.probe_status_state_granted)
        refreshUntilRowShows(PermissionStatusItem.ExactAlarms, R.string.probe_status_state_granted)
    }

    @Test
    fun promotedNotificationsAreUnavailableOnlyBeforeAndroid16() {
        val row = composeRule.onNodeWithTag(permissionStatusRowTag(PermissionStatusItem.PromotedNotifications))
        val unavailable = context.getString(R.string.probe_status_state_unavailable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val shown = row.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            assertNotEquals(unavailable, shown)
        } else {
            row.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, unavailable))
        }
    }

    @Test
    fun fullScreenIntentStateFollowsTheAppOpAfterRefresh() {
        shell("appops set $packageName USE_FULL_SCREEN_INTENT deny")
        refreshUntilRowShows(PermissionStatusItem.FullScreenIntent, R.string.probe_status_state_denied)

        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        refreshUntilRowShows(PermissionStatusItem.FullScreenIntent, R.string.probe_status_state_granted)
    }

    @Test
    fun batteryStateFollowsTheExemptionAfterRefresh() {
        shell("cmd deviceidle whitelist -$packageName")
        refreshUntilRowShows(PermissionStatusItem.BatteryOptimization, R.string.probe_status_state_denied)

        shell("cmd deviceidle whitelist +$packageName")
        refreshUntilRowShows(PermissionStatusItem.BatteryOptimization, R.string.probe_status_state_granted)
    }

    @Test
    fun eachRowOpensItsSettingsPage() {
        // Fixed states, so the expected pages are known: battery still optimised.
        shell("cmd deviceidle whitelist -$packageName")
        refreshUntilRowShows(PermissionStatusItem.Notifications, R.string.probe_status_state_granted)
        refreshUntilRowShows(PermissionStatusItem.BatteryOptimization, R.string.probe_status_state_denied)

        val expected = buildMap {
            put(PermissionStatusItem.Notifications, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                put(PermissionStatusItem.PromotedNotifications, Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            }
            put(PermissionStatusItem.FullScreenIntent, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            put(PermissionStatusItem.ExactAlarms, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            put(PermissionStatusItem.BatteryOptimization, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }

        expected.forEach { (item, action) ->
            val started = interceptActivityStarts {
                composeRule.onNodeWithTag(permissionStatusRowTag(item)).performClick()
                composeRule.waitForIdle()
            }
            assertEquals("$item opens", action, started.firstOrNull()?.action)
        }
    }

    /** Pauses and resumes the list until the row shows [stateRes]; each resume re-reads the states. */
    private fun refreshUntilRowShows(item: PermissionStatusItem, @StringRes stateRes: Int) {
        val expected = context.getString(stateRes)
        val matcher = hasTestTag(permissionStatusRowTag(item)) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)
        val deadline = SystemClock.uptimeMillis() + REFRESH_TIMEOUT_MS
        while (true) {
            composeRule.runOnUiThread {
                listLifecycle.registry.currentState = Lifecycle.State.STARTED
                listLifecycle.registry.currentState = Lifecycle.State.RESUMED
            }
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            check(SystemClock.uptimeMillis() < deadline) { "$item did not show \"$expected\" after refresh" }
            // System state (app ops, permission caches) can take a moment to propagate.
            SystemClock.sleep(REFRESH_RETRY_MS)
        }
    }

    /** Records the activities started during [block] without launching them. */
    private fun interceptActivityStarts(block: () -> Unit): List<Intent> {
        val monitor = RecordingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            block()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        return monitor.intents
    }

    /** A lifecycle the test controls; created and driven on the main thread. */
    private class ListLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class RecordingMonitor : Instrumentation.ActivityMonitor() {
        val intents = CopyOnWriteArrayList<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            intents += intent
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    private fun shell(command: String): String {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 10_000L
        const val REFRESH_RETRY_MS = 250L
    }
}
