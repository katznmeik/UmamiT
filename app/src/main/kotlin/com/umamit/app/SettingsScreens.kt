package com.umamit.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.umamit.core.data.SettingsRepository
import com.umamit.feature.settings.AboutScreen
import com.umamit.feature.settings.SettingsRoute
import com.umamit.feature.settings.backup.ImportedBackup
import com.umamit.feature.settings.backup.ImportedBackupSnackbar
import com.umamit.feature.settings.diagnostics.DiagnosticsRoute
import com.umamit.feature.teaware.ArchivedVessel
import com.umamit.feature.teaware.TeawareRoute
import com.umamit.feature.teaware.VesselEditorRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.umamit.feature.onboarding.TourRoute as TourScreenRoute

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
    /** *Daten & Sicherung* (SET-30): SCR-26. */
    val onOpenDataBackup: () -> Unit = {},
    /** *Einführung* (SET-36): the tour, SCR-33. */
    val onOpenTour: () -> Unit = {},
)

/**
 * What *Daten & Sicherung* (SCR-26) gets from its host: [onImported] after a finished import (Start with its
 * snackbar, BAK-02 AC6); no dialog stays over the alert [alertKey] (rule 8).
 */
internal class DataBackupHost(val onBack: () -> Unit, val onImported: (ImportedBackup) -> Unit, val alertKey: String?)

/**
 * What *Teegeschirr* (SCR-13) gets from its host: [archived] is the vessel the editor above just archived,
 * for the snackbar; [onArchivedSnackbarEnd] once it is gone.
 */
internal class TeawareHost(
    val onBack: () -> Unit,
    val onOpenVessel: (vesselId: String) -> Unit,
    val onAddVessel: () -> Unit,
    val archived: ArchivedVessel? = null,
    val onArchivedSnackbarEnd: () -> Unit = {},
)

/**
 * What the vessel editor (SCR-14) gets from its host; its menus and dialogs give way to another alert than
 * [alertKey] (rule 8).
 */
internal class VesselEditorHost(
    val onClose: () -> Unit,
    val onSave: (vesselId: String) -> Unit,
    val onArchive: (ArchivedVessel) -> Unit,
    val alertKey: String? = null,
)

/**
 * How one graph navigates from the settings (SCR-25) and the screens opened from it (D-125): built once per graph
 * with that graph's own lambdas, since the root above the timer and a tab's stack navigate differently.
 *
 * @property back pops the screen on top; every on-screen back acts only while its screen is resumed (D-090).
 * @property open opens *Über*, *Diagnose* or *Teegeschirr* (above the timer, it records the alert: rule 8).
 * @property openVessel opens a vessel's editor from *Teegeschirr*; only from the resumed list when
 *   [openVesselResumedOnly] (D-090).
 * @property addVessel *Neues Gefäß* on *Teegeschirr*; acts only while the list is resumed.
 * @property closeVesselEditor closes the vessel editor with its result (`NavController.closeVesselEditor`).
 * @property alertKey the alert playing now, or null: no dialog of these screens stays over it (rule 8). It is read in
 *   each screen's content, so a new alert reaches the screen whatever the graph's lifetime.
 * @property scrollToTopRequest the tab's re-select request for the settings (03 §2.2); 0 above the timer.
 * @property rerunSetup *Einrichtung erneut zeigen* on *Über* (ONB-04).
 * @property openTour *Einführung* under *Hilfe* (SET-36): the tour above the shell or the timer.
 * @property backupImported a finished import on *Daten & Sicherung* (BAK-02 AC6): the root shows Start.
 */
internal class SettingsNav(
    val back: () -> Unit,
    val open: (route: Any) -> Unit,
    val openVessel: (vesselId: String) -> Unit,
    val openVesselResumedOnly: Boolean,
    val addVessel: () -> Unit,
    val closeVesselEditor: (savedVesselId: String?, archived: ArchivedVessel?) -> Unit,
    val alertKey: () -> String?,
    val scrollToTopRequest: Int = 0,
    val rerunSetup: () -> Unit,
    val openTour: () -> Unit = {},
    val backupImported: (ImportedBackup) -> Unit = {},
)

/** What *Über* (SCR-27) gets from its host. */
internal class AboutHost(val onBack: () -> Unit, val onRerunSetup: () -> Unit, val onOpenDiagnostics: () -> Unit)

/**
 * The settings screens of M1 (SCR-25, SCR-27 with *Diagnose*), *Daten & Sicherung* (SCR-26) with the import's
 * snackbar on Start, the root's [onRerunSetup] (*Einrichtung erneut zeigen*, ONB-04) and [onBackupImported] (BAK-02
 * AC6). The app uses the feature routes; tests replace them, since those take their view models from Hilt.
 */
internal class SettingsScreens(
    val onRerunSetup: () -> Unit = {},
    val onBackupImported: (ImportedBackup) -> Unit = {},
    val onOpenTour: () -> Unit = {},
    val screen: @Composable (SettingsHost) -> Unit = { host ->
        SettingsRoute(
            onBack = host.onBack,
            onOpenAbout = host.onOpenAbout,
            alertKey = host.alertKey,
            scrollToTopRequest = host.scrollToTopRequest,
            onOpenTeaware = host.onOpenTeaware,
            onOpenDataBackup = host.onOpenDataBackup,
            onOpenTour = host.onOpenTour,
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
    /** The tour (SCR-33): *Fertig*, *Überspringen* and back all call `onClose`. */
    val tour: @Composable (onClose: () -> Unit) -> Unit = { onClose -> TourScreenRoute(onClose = onClose) },
    val teaware: @Composable (TeawareHost) -> Unit = { host ->
        TeawareRoute(
            onBack = host.onBack,
            onOpenVessel = host.onOpenVessel,
            onAddVessel = host.onAddVessel,
            archived = host.archived,
            onArchivedSnackbarEnd = host.onArchivedSnackbarEnd,
        )
    },
    val vesselEditor: @Composable (VesselEditorHost) -> Unit = { host ->
        VesselEditorRoute(
            onClose = host.onClose,
            onSave = host.onSave,
            onArchive = host.onArchive,
            alertKey = host.alertKey,
        )
    },
    val dataBackup: @Composable (DataBackupHost) -> Unit = { host ->
        com.umamit.feature.settings.backup.DataBackupRoute(
            onBack = host.onBack,
            onImportFinish = host.onImported,
            alertKey = host.alertKey,
        )
    },
    /** „Sicherung eingespielt · …“ · *Rückgängig machen* in the shell's snackbar queue (03 SCR-26). */
    val importedSnackbar: @Composable (imported: ImportedBackup?, onDone: () -> Unit, host: SnackbarHostState) -> Unit =
        { imported, onDone, host -> if (imported != null) ImportedBackupSnackbar(imported, onDone, hostState = host) },
) {
    /** The same screens with the root's [onRerunSetup], [onBackupImported] and [onOpenTour]. */
    fun withRoot(onRerunSetup: () -> Unit, onBackupImported: (ImportedBackup) -> Unit, onOpenTour: () -> Unit) =
        SettingsScreens(
            onRerunSetup,
            onBackupImported,
            onOpenTour,
            screen,
            about,
            diagnostics,
            tour,
            teaware,
            vesselEditor,
            dataBackup,
            importedSnackbar,
        )
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
