package com.umamit.core.timer.reliability

import android.content.Intent
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.diagnostics.PermissionStates
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reliability checks behind the setup page (ONB-02), the settings card (SET-24) and the banner
 * (TMR-31): the probe's permission states (#14) as ✓/! per check, and *Beheben* opening the
 * failing check's own system page (03 §2.4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerReliabilityTest {

    private val allGranted = PermissionStates(
        notifications = true,
        promotedNotifications = true,
        fullScreenIntent = true,
        exactAlarms = true,
        batteryUnrestricted = true,
    )

    private fun reliability(states: PermissionStates) = AndroidTimerReliability(reader = {
        states
    }, packageName = PACKAGE)

    @Test
    fun `every check passing is no problem, and a platform without Live Updates is not one either`() {
        assertThat(reliability(allGranted).status().hasProblem).isFalse()

        val noPromotion = reliability(allGranted.copy(promotedNotifications = null)).status()

        assertThat(noPromotion.hasProblem).isFalse()
        assertThat(noPromotion.stateOf(ReliabilityCheck.LiveUpdates)).isEqualTo(CheckState.Unavailable)
    }

    @Test
    fun `each missing permission is a missing check`() {
        val status = reliability(
            PermissionStates(
                notifications = false,
                promotedNotifications = false,
                fullScreenIntent = false,
                exactAlarms = false,
                batteryUnrestricted = false,
            ),
        ).status()

        assertThat(ReliabilityCheck.entries.map(status::stateOf).toSet()).containsExactly(CheckState.Missing)
        assertThat(status.hasProblem).isTrue()
        assertThat(status.firstMissing).isEqualTo(ReliabilityCheck.Notifications)
    }

    @Test
    fun `Beheben opens the first missing check in the card's order`() {
        val reliability = reliability(allGranted.copy(fullScreenIntent = false, batteryUnrestricted = false))

        assertThat(reliability.firstMissingIntents().first().action)
            .isEqualTo(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }

    @Test
    fun `nothing missing, Beheben opens nothing`() {
        assertThat(reliability(allGranted).firstMissingIntents()).isEmpty()
    }

    @Test
    fun `each check opens its own page, with the app details page last`() {
        val reliability = reliability(allGranted.copy(notifications = false, batteryUnrestricted = false))
        val opened: List<List<Intent>> = ReliabilityCheck.entries.map(reliability::settingsIntents)

        assertThat(opened.map { it.first().action }).containsExactly(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        ).inOrder()
        assertThat(opened.map { it.last().action }.toSet())
            .containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(opened[0].first().getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(PACKAGE)
    }

    @Test
    fun `an unrestricted battery opens the app's details, where One UI keeps Uneingeschraenkt`() {
        val intents = reliability(allGranted).settingsIntents(ReliabilityCheck.Battery)

        assertThat(intents.map { it.action }).containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    @Test
    fun `the Samsung auto-restart link tries Device care first, then the settings`() {
        val intents = samsungAutoRestartIntents()

        assertThat(intents.first().component?.packageName).isEqualTo("com.samsung.android.lool")
        assertThat(intents.last().action).isEqualTo(Settings.ACTION_SETTINGS)
    }

    private companion object {
        const val PACKAGE = "com.umamit.app"
    }
}
