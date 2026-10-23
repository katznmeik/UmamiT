package com.umamit.app

import androidx.compose.runtime.Composable
import com.umamit.core.data.SettingsRepository
import com.umamit.feature.settings.AboutScreen
import com.umamit.feature.settings.SettingsRoute
import com.umamit.feature.settings.diagnostics.DiagnosticsRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * What the settings (SCR-25) get from their host, in a tab's stack or above the timer.
 *
 * @property alertKey the alert playing now, or null: no dialog of the settings stays over it (rule 8).
 */
internal class SettingsHost(
    val onBack: () -> Unit,
    val onOpenAbout: () -> Unit,
    val alertKey: String?,
    val scrollToTopRequest: Int = 0,
)

/** What *Über* (SCR-27) gets from its host. */
internal class AboutHost(val onBack: () -> Unit, val onRerunSetup: () -> Unit, val onOpenDiagnostics: () -> Unit)

/**
 * The settings screens of M1 (SCR-25, SCR-27 with *Diagnose*) and the root's [onRerunSetup]
 * (*Einrichtung erneut zeigen*, ONB-04). The app uses the feature routes; tests replace them,
 * since those take their view models from Hilt.
 */
internal class SettingsScreens(
    val onRerunSetup: () -> Unit = {},
    val screen: @Composable (SettingsHost) -> Unit = { host ->
        SettingsRoute(
            onBack = host.onBack,
            onOpenAbout = host.onOpenAbout,
            alertKey = host.alertKey,
            scrollToTopRequest = host.scrollToTopRequest,
        )
    },
    val about: @Composable (AboutHost) -> Unit = { host ->
        AboutScreen(
            versionName = BuildConfig.VERSION_NAME,
            onBack = host.onBack,
            onRerunSetup = host.onRerunSetup,
            onOpenDiagnostics = host.onOpenDiagnostics,
        )
    },
    val diagnostics: @Composable (onBack: () -> Unit) -> Unit = { onBack -> DiagnosticsRoute(onBack = onBack) },
) {
    /** The same screens with the root's [onRerunSetup]. */
    fun withRerunSetup(onRerunSetup: () -> Unit) = SettingsScreens(onRerunSetup, screen, about, diagnostics)
}

/**
 * The app's settings store for code outside Hilt's injection: the instrumented tests mark the
 * first-start setup done before the activity starts, so they open on Start (LaunchSmokeTest).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settings(): SettingsRepository
}
