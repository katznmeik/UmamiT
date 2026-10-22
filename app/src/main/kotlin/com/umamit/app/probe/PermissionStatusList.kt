package com.umamit.app.probe

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.umamit.app.R

/**
 * The probe's permission status list (M1-03a, #14; 09 §4): one row per
 * [PermissionStatusItem] with ✓/✗, a German label and explanation. Tapping a
 * row opens the matching settings page (04 §6.2, §6.4, §8.2, §8.3); the
 * notifications row first asks for `POST_NOTIFICATIONS` while it is not
 * granted. The states are read again whenever the screen resumes, so the list
 * is current after returning from settings.
 *
 * Self-contained: it reads the platform state itself. A plain [Column], so it
 * can sit inside a scrolling probe screen.
 */
@Composable
fun PermissionStatusList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val reader = remember(context) { PermissionStatusReader(AndroidPermissionStatusChecks(context)) }
    var rows by remember(reader) { mutableStateOf(reader.read()) }

    LifecycleResumeEffect(reader) {
        rows = reader.read()
        onPauseOrDispose { }
    }

    fun openSettings(row: PermissionStatusRow) {
        context.startFirstPermissionStatusIntent(
            permissionStatusSettingsIntents(row.item, row.state, context.packageName),
        )
    }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        rows = reader.read()
        // No dialog was shown (denied for good, or dismissed): only the settings page can grant it now.
        val deniedForGood =
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        if (!granted && deniedForGood) {
            openSettings(PermissionStatusRow(PermissionStatusItem.Notifications, PermissionState.Denied))
        }
    }

    PermissionStatusListContent(
        rows = rows,
        onRowClick = { row ->
            if (row.item == PermissionStatusItem.Notifications && !isNotificationPermissionGranted(context)) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings(row)
            }
        },
        modifier = modifier,
    )
}

/** Test tag of a status row, for UI tests. */
fun permissionStatusRowTag(item: PermissionStatusItem): String = "PermissionStatusRow_${item.name}"

/** Stateless list: renders [rows]; a tap on an available row calls [onRowClick]. */
@Composable
internal fun PermissionStatusListContent(
    rows: List<PermissionStatusRow>,
    onRowClick: (PermissionStatusRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.probe_status_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        rows.forEach { row ->
            PermissionStatusRowItem(row = row, onClick = { onRowClick(row) })
        }
    }
}

@Composable
private fun PermissionStatusRowItem(row: PermissionStatusRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stateText = stringResource(row.state.textRes())
    val symbolColor = when (row.state) {
        PermissionState.Granted -> MaterialTheme.colorScheme.primary
        PermissionState.Denied -> MaterialTheme.colorScheme.error
        PermissionState.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = { Text(stringResource(row.item.labelRes())) },
        supportingContent = {
            Column {
                Text(stringResource(row.item.explanationRes()))
                Text(text = stateText, color = symbolColor, style = MaterialTheme.typography.labelLarge)
            }
        },
        trailingContent = {
            Text(
                text = stringResource(row.state.symbolRes()),
                color = symbolColor,
                style = MaterialTheme.typography.headlineSmall,
                // The state is announced through the row's state description.
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        },
        modifier = modifier
            .testTag(permissionStatusRowTag(row.item))
            .clickable(enabled = row.state != PermissionState.Unavailable, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = stateText },
    )
}

@StringRes
private fun PermissionStatusItem.labelRes(): Int = when (this) {
    PermissionStatusItem.Notifications -> R.string.probe_status_notifications_label
    PermissionStatusItem.PromotedNotifications -> R.string.probe_status_promoted_label
    PermissionStatusItem.FullScreenIntent -> R.string.probe_status_full_screen_label
    PermissionStatusItem.ExactAlarms -> R.string.probe_status_exact_alarms_label
    PermissionStatusItem.BatteryOptimization -> R.string.probe_status_battery_label
}

@StringRes
private fun PermissionStatusItem.explanationRes(): Int = when (this) {
    PermissionStatusItem.Notifications -> R.string.probe_status_notifications_explanation
    PermissionStatusItem.PromotedNotifications -> R.string.probe_status_promoted_explanation
    PermissionStatusItem.FullScreenIntent -> R.string.probe_status_full_screen_explanation
    PermissionStatusItem.ExactAlarms -> R.string.probe_status_exact_alarms_explanation
    PermissionStatusItem.BatteryOptimization -> R.string.probe_status_battery_explanation
}

@StringRes
private fun PermissionState.textRes(): Int = when (this) {
    PermissionState.Granted -> R.string.probe_status_state_granted
    PermissionState.Denied -> R.string.probe_status_state_denied
    PermissionState.Unavailable -> R.string.probe_status_state_unavailable
}

@StringRes
private fun PermissionState.symbolRes(): Int = when (this) {
    PermissionState.Granted -> R.string.probe_status_symbol_granted
    PermissionState.Denied -> R.string.probe_status_symbol_denied
    PermissionState.Unavailable -> R.string.probe_status_symbol_unavailable
}
