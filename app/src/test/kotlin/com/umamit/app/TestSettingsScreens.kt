package com.umamit.app

import androidx.compose.material3.Text
import com.umamit.core.model.UserSettings
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.feature.settings.AboutScreen
import com.umamit.feature.settings.SettingsActions
import com.umamit.feature.settings.SettingsScreen

/**
 * The settings screens without Hilt: the stateless SCR-25 with the defaults and every check
 * passing, the real *Über*, and a stand-in log for *Diagnose*.
 */
internal fun testSettingsScreens(): SettingsScreens = SettingsScreens(
    screen = { host ->
        SettingsScreen(
            settings = UserSettings(),
            status = ReliabilityStatus.AllOk,
            alerting = host.alertKey != null,
            onBack = host.onBack,
            actions = SettingsActions(openAbout = host.onOpenAbout),
            scrollToTopRequest = host.scrollToTopRequest,
        )
    },
    about = { host ->
        AboutScreen(
            versionName = "0.1.0-test",
            onBack = host.onBack,
            onRerunSetup = host.onRerunSetup,
            onOpenDiagnostics = host.onOpenDiagnostics,
        )
    },
    diagnostics = { Text("DIAGNOSE-LOG") },
)
