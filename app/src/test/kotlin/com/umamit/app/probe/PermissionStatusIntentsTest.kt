package com.umamit.app.probe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Deep links of the probe status rows (M1-03a, #14; 04 §6.2, §6.4, §8.2, §8.3) and their fallback. */
// SDK 35: Robolectric's SDK 36 sandbox needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`
// on JDK 21 (ApplicationSharedMemory), which the shared test configuration does not pass yet.
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class PermissionStatusIntentsTest {

    private val packageUri = Uri.parse("package:$PACKAGE")

    private fun intents(item: PermissionStatusItem, state: PermissionState = PermissionState.Denied, sdkInt: Int = 36) =
        permissionStatusSettingsIntents(item, state, PACKAGE, sdkInt)

    private fun Intent.assertAppDetails() {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, action)
        assertEquals(packageUri, data)
    }

    @Test
    fun everyRowEndsWithTheAppDetailsPage() {
        for (item in PermissionStatusItem.entries) {
            for (state in PermissionState.entries) {
                intents(item, state).last().assertAppDetails()
            }
        }
    }

    @Test
    fun notificationsOpenTheAppNotificationSettings() {
        val first = intents(PermissionStatusItem.Notifications).first()

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, first.action)
        assertEquals(PACKAGE, first.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun promotedNotificationsOpenThePromotionPageOnAndroid16() {
        val all = intents(PermissionStatusItem.PromotedNotifications, sdkInt = 36)

        assertEquals(
            listOf(
                Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ),
            all.map { it.action },
        )
        assertEquals(PACKAGE, all.first().getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun promotedNotificationsBeforeAndroid16OpenTheNotificationSettings() {
        val all = intents(PermissionStatusItem.PromotedNotifications, sdkInt = 35)

        assertEquals(
            listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            all.map { it.action },
        )
    }

    @Test
    fun fullScreenIntentOpensItsPageForThisApp() {
        val first = intents(PermissionStatusItem.FullScreenIntent).first()

        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, first.action)
        assertEquals(packageUri, first.data)
    }

    @Test
    fun exactAlarmsOpenTheirPageForThisApp() {
        val first = intents(PermissionStatusItem.ExactAlarms).first()

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, first.action)
        assertEquals(packageUri, first.data)
    }

    @Test
    fun batteryWhileOptimisedRequestsTheExemptionWithListFallback() {
        val all = intents(PermissionStatusItem.BatteryOptimization, PermissionState.Denied)

        assertEquals(
            listOf(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ),
            all.map { it.action },
        )
        assertEquals(packageUri, all.first().data)
    }

    @Test
    fun batteryOnceExemptOpensTheAppDetails() {
        val all = intents(PermissionStatusItem.BatteryOptimization, PermissionState.Granted)

        assertEquals(1, all.size)
        all.single().assertAppDetails()
    }

    @Test
    fun startsTheFirstIntentThatAnActivityHandles() {
        val context = RecordingContext(
            failingActions = setOf(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS),
        )

        val started = context.startFirstPermissionStatusIntent(intents(PermissionStatusItem.PromotedNotifications))

        assertTrue(started)
        assertEquals(listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS), context.started.map { it.action })
        // Not an activity context: the settings page needs its own task.
        assertTrue(context.started.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun securityExceptionAlsoFallsBack() {
        val context = RecordingContext(
            failingActions = setOf(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
            failure = { SecurityException("denied") },
        )

        context.startFirstPermissionStatusIntent(intents(PermissionStatusItem.BatteryOptimization))

        assertEquals(
            listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            context.started.map { it.action },
        )
    }

    @Test
    fun returnsFalseWhenNothingResolves() {
        val all = intents(PermissionStatusItem.FullScreenIntent)
        val context = RecordingContext(failingActions = all.mapNotNull { it.action }.toSet())

        assertFalse(context.startFirstPermissionStatusIntent(all))
        assertNull(context.started.firstOrNull())
    }

    private class RecordingContext(
        private val failingActions: Set<String>,
        private val failure: () -> RuntimeException = { ActivityNotFoundException() },
        base: Context = RuntimeEnvironment.getApplication(),
    ) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            if (intent.action in failingActions) throw failure()
            started += intent
        }
    }

    private companion object {
        const val PACKAGE = "com.umamit.app.test"
    }
}
