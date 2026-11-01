package com.umamit.app

import androidx.compose.runtime.Composable
import com.umamit.core.data.SettingsRepository
import com.umamit.feature.settings.AboutScreen
import com.umamit.feature.settings.SettingsRoute
import com.umamit.feature.settings.diagnostics.DiagnosticsRoute
import com.umamit.feature.teaware.ArchivedVessel
import com.umamit.feature.teaware.TeawareRoute
import com.umamit.feature.teaware.VesselEditorRoute
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
    /** *Teegeschirr* (SET-30): the teaware list (SCR-13). */
    val onOpenTeaware: () -> Unit = {},
)

/**
 * What *Teegeschirr* (SCR-13) gets from its host: [archived] is the vessel the editor above just archived,
 * for the snackbar; [onArchivedShown] once it is gone.
 */
internal class TeawareHost(
    val onBack: () -> Unit,
    val onOpenVessel: (vesselId: String) -> Unit,
    val onAddVessel: () -> Unit,
    val archived: ArchivedVessel? = null,
    val onArchivedShown: () -> Unit = {},
)

/** What the vessel editor (SCR-14) gets from its host. */
internal class VesselEditorHost(
    val onClose: () -> Unit,
    val onSaved: (vesselId: String) -> Unit,
    val onArchived: (ArchivedVessel) -> Unit,
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
            onOpenTeaware = host.onOpenTeaware,
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
    val teaware: @Composable (TeawareHost) -> Unit = { host ->
        TeawareRoute(
            onBack = host.onBack,
            onOpenVessel = host.onOpenVessel,
            onAddVessel = host.onAddVessel,
            archived = host.archived,
            onArchivedShown = host.onArchivedShown,
        )
    },
    val vesselEditor: @Composable (VesselEditorHost) -> Unit = { host ->
        VesselEditorRoute(onClose = host.onClose, onSaved = host.onSaved, onArchived = host.onArchived)
    },
) {
    /** The same screens with the root's [onRerunSetup]. */
    fun withRerunSetup(onRerunSetup: () -> Unit) =
        SettingsScreens(onRerunSetup, screen, about, diagnostics, teaware, vesselEditor)
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
