package com.umamit.app.probe

import org.junit.Assert.assertEquals
import org.junit.Test

/** State logic of the probe status list (M1-03a, #14): each check maps to its row's state. */
class PermissionStatusReaderTest {

    private class FakeChecks(
        var notifications: Boolean = true,
        var promoted: Boolean? = true,
        var fullScreen: Boolean = true,
        var exactAlarms: Boolean = true,
        var ignoringBatteryOptimizations: Boolean = true,
    ) : PermissionStatusChecks {
        override fun areNotificationsEnabled() = notifications
        override fun canPostPromotedNotifications() = promoted
        override fun canUseFullScreenIntent() = fullScreen
        override fun canScheduleExactAlarms() = exactAlarms
        override fun isIgnoringBatteryOptimizations() = ignoringBatteryOptimizations
    }

    private fun PermissionStatusReader.stateOf(item: PermissionStatusItem) = read().single { it.item == item }.state

    @Test
    fun rowsFollowTheDisplayOrder() {
        val rows = PermissionStatusReader(FakeChecks()).read()

        assertEquals(
            listOf(
                PermissionStatusItem.Notifications,
                PermissionStatusItem.PromotedNotifications,
                PermissionStatusItem.FullScreenIntent,
                PermissionStatusItem.ExactAlarms,
                PermissionStatusItem.BatteryOptimization,
            ),
            rows.map { it.item },
        )
    }

    @Test
    fun allChecksTrueAreAllGranted() {
        val rows = PermissionStatusReader(FakeChecks()).read()

        assertEquals(List(5) { PermissionState.Granted }, rows.map { it.state })
    }

    @Test
    fun allChecksFalseAreAllDenied() {
        val checks = FakeChecks(
            notifications = false,
            promoted = false,
            fullScreen = false,
            exactAlarms = false,
            ignoringBatteryOptimizations = false,
        )

        val rows = PermissionStatusReader(checks).read()

        assertEquals(List(5) { PermissionState.Denied }, rows.map { it.state })
    }

    @Test
    fun eachCheckDrivesOnlyItsOwnRow() {
        val cases: List<Pair<PermissionStatusItem, (FakeChecks) -> Unit>> = listOf(
            PermissionStatusItem.Notifications to { it.notifications = false },
            PermissionStatusItem.PromotedNotifications to { it.promoted = false },
            PermissionStatusItem.FullScreenIntent to { it.fullScreen = false },
            PermissionStatusItem.ExactAlarms to { it.exactAlarms = false },
            PermissionStatusItem.BatteryOptimization to { it.ignoringBatteryOptimizations = false },
        )
        for ((deniedItem, deny) in cases) {
            val checks = FakeChecks().also(deny)

            val rows = PermissionStatusReader(checks).read()

            rows.forEach { row ->
                val expected = if (row.item == deniedItem) PermissionState.Denied else PermissionState.Granted
                assertEquals("$deniedItem denied → ${row.item}", expected, row.state)
            }
        }
    }

    @Test
    fun promotedNotificationsWithoutApiAreUnavailable() {
        val reader = PermissionStatusReader(FakeChecks(promoted = null))

        assertEquals(PermissionState.Unavailable, reader.stateOf(PermissionStatusItem.PromotedNotifications))
    }

    @Test
    fun readReflectsChangedChecks() {
        val checks = FakeChecks(fullScreen = false)
        val reader = PermissionStatusReader(checks)
        assertEquals(PermissionState.Denied, reader.stateOf(PermissionStatusItem.FullScreenIntent))

        checks.fullScreen = true

        assertEquals(PermissionState.Granted, reader.stateOf(PermissionStatusItem.FullScreenIntent))
    }
}
