package com.umamit.app

import androidx.compose.material3.Text
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeVesselArchive
import com.umamit.core.timer.reliability.ReliabilityStatus
import com.umamit.feature.settings.AboutScreen
import com.umamit.feature.settings.SettingsActions
import com.umamit.feature.settings.SettingsScreen
import com.umamit.feature.teaware.TeawareRoute
import com.umamit.feature.teaware.TeawareViewModel
import com.umamit.feature.teaware.VesselEditorRoute
import com.umamit.feature.teaware.VesselEditorViewModel

/**
 * The settings screens without Hilt: the stateless SCR-25 with the defaults and every check
 * passing, the real *Über*, a stand-in log for *Diagnose*, and *Teegeschirr* with its editor on
 * [collection]'s vessels.
 */
internal fun testSettingsScreens(collection: FakeCollection = FakeCollection()): SettingsScreens {
    val archive = FakeVesselArchive(FakeSessions(collection))
    return SettingsScreens(
        screen = { host ->
            SettingsScreen(
                settings = UserSettings(),
                status = ReliabilityStatus.AllOk,
                alerting = host.alertKey != null,
                onBack = host.onBack,
                actions = SettingsActions(openAbout = host.onOpenAbout, openTeaware = host.onOpenTeaware),
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
        teaware = { host ->
            TeawareRoute(
                onBack = host.onBack,
                onOpenVessel = host.onOpenVessel,
                onAddVessel = host.onAddVessel,
                archived = host.archived,
                onArchivedShown = host.onArchivedShown,
                viewModel = viewModel { TeawareViewModel(collection.vessels, archive) },
            )
        },
        vesselEditor = { host ->
            VesselEditorRoute(
                onClose = host.onClose,
                onSaved = host.onSaved,
                onArchived = host.onArchived,
                // The route's vesselId arrives in the entry's SavedStateHandle, as with Hilt.
                viewModel = viewModel { VesselEditorViewModel(createSavedStateHandle(), collection.vessels, archive) },
            )
        },
    )
}
