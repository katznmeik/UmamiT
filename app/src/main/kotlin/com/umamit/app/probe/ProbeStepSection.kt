package com.umamit.app.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.umamit.app.R
import com.umamit.core.timer.probe.ProbePhase
import com.umamit.core.timer.probe.ProbeStatus
import com.umamit.core.timer.probe.ProbeTimer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Probe step section (M1-03a, #15): one tap starts the 1-min test step with
 * exact alarms, the foreground service with the Live Update and the
 * full-screen alert at the end. Asks for the notification permission first
 * when it is missing.
 */
@Composable
fun ProbeStepSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by ProbeTimer.status.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way: without the permission the alarms and the service still run.
        ProbeTimer.start(context)
    }
    ProbeStepContent(
        status = status,
        onStart = {
            if (context.hasNotificationPermission()) {
                ProbeTimer.start(context)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onCancel = { ProbeTimer.stop(context) },
        modifier = modifier,
    )
}

@Composable
private fun ProbeStepContent(
    status: ProbeStatus,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.probe_step_heading),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.probe_step_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = statusText(status),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (status is ProbeStatus.Active && !status.exactAlarms) {
            Text(
                text = stringResource(R.string.probe_step_inexact),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (status is ProbeStatus.Active) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.probe_step_cancel))
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.probe_step_start))
            }
        }
    }
}

@Composable
private fun statusText(status: ProbeStatus): String = when (status) {
    ProbeStatus.Idle -> stringResource(R.string.probe_step_status_idle)

    is ProbeStatus.Active -> {
        val endTime = TIME_FORMAT.format(Instant.ofEpochMilli(status.step.deadlineWallMs))
        when (status.phase) {
            ProbePhase.RUNNING -> stringResource(R.string.probe_step_status_running, endTime)
            ProbePhase.PRE_ALERTED -> stringResource(R.string.probe_step_status_pre_alert, endTime)
            ProbePhase.FINISHED -> stringResource(R.string.probe_step_status_finished)
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.GERMANY).withZone(ZoneId.systemDefault())

private fun Context.hasNotificationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true)
@Composable
private fun ProbeStepContentIdlePreview() {
    MaterialTheme {
        ProbeStepContent(status = ProbeStatus.Idle, onStart = {}, onCancel = {})
    }
}
