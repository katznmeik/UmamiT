package com.umamit.core.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The action of the notifications row (ONB-02, SET-24; verified on the Galaxy, #14): asks for
 * `POST_NOTIFICATIONS` while it is not granted; when it is granted (notifications switched off in
 * the settings) or Android shows no dialog any more (denied for good), [openSettings] opens the
 * app's notification settings. [onResult] runs after the dialog, so the rows read again.
 */
@Composable
fun rememberNotificationPermissionAction(onResult: () -> Unit, openSettings: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOpenSettings by rememberUpdatedState(openSettings)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult()
        val deniedForGood =
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        if (!granted && deniedForGood) currentOpenSettings()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) currentOpenSettings() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
