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
 * (TMR-31): the permission states (#14) as ✓/! per check, and *Beheben* opening the
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
                alarmVolumeOn = false,
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
            Settings.ACTION_SOUND_SETTINGS,
        ).inOrder()
        // The app's own pages end with its details page; the alarm volume is a phone setting: the settings.
        assertThat(opened.dropLast(1).map { it.last().action }.toSet())
            .containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(opened.last().last().action).isEqualTo(Settings.ACTION_SETTINGS)
        assertThat(opened[0].first().getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(PACKAGE)
    }

    @Test
    fun `the alarm volume at 0 is a missing check of its own, last in the card, and clears when raised`() {
        // TMR-31, D-098: the cues play on the alarm stream.
        val off = reliability(allGranted.copy(alarmVolumeOn = false)).status()

        assertThat(ReliabilityCheck.entries.last()).isEqualTo(ReliabilityCheck.AlarmVolume)
        assertThat(off.stateOf(ReliabilityCheck.AlarmVolume)).isEqualTo(CheckState.Missing)
        assertThat(off.firstMissing).isEqualTo(ReliabilityCheck.AlarmVolume)
        assertThat(off.problem).isEqualTo(ReliabilityProblem.AlarmVolumeOff)

        val raised = reliability(allGranted.copy(alarmVolumeOn = true)).status()
        assertThat(raised.stateOf(ReliabilityCheck.AlarmVolume)).isEqualTo(CheckState.Ok)
        assertThat(raised.problem).isNull()
    }

    @Test
    fun `the banner names the alarm volume only when it is the check Beheben opens`() {
        // „Alarmlautstärke ist aus" when the volume is the first missing check; with another check missing
        // before it, the general message, and Beheben opens that check's page.
        val alsoBattery = reliability(allGranted.copy(alarmVolumeOn = false, batteryUnrestricted = false))

        assertThat(alsoBattery.status().problem).isEqualTo(ReliabilityProblem.Unreliable)
        assertThat(alsoBattery.firstMissingIntents().first().action)
            .isEqualTo(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        assertThat(reliability(allGranted.copy(alarmVolumeOn = false)).firstMissingIntents().first().action)
            .isEqualTo(Settings.ACTION_SOUND_SETTINGS)
        assertThat(reliability(allGranted.copy(exactAlarms = false)).status().problem)
            .isEqualTo(ReliabilityProblem.Unreliable)
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
