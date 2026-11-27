package com.umamit.app

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.umamit.feature.onboarding.howto.HowToTopic

/**
 * Registers the settings [S] (the root's `TimerSettingsRoute`, a tab's `SettingsRoute`) and the four screens opened
 * from them: *Über*, *Diagnose*, *Teegeschirr*, the vessel editor (D-125) and *Daten & Sicherung* (SCR-26).
 */
internal inline fun <reified S : Any> NavGraphBuilder.settingsRoutes(settings: SettingsScreens, nav: SettingsNav) {
    composable<S> {
        val howTo = LocalHowTo.current
        settings.screen(
            SettingsHost(
                onBack = dropUnlessResumed(block = nav.back),
                onOpenAbout = { nav.open(AboutRoute) },
                alertKey = nav.alertKey(),
                scrollToTopRequest = nav.scrollToTopRequest,
                onOpenTeaware = dropUnlessResumed { nav.open(TeawareRoute) },
                onOpenDataBackup = dropUnlessResumed { nav.open(DataBackupRoute) },
                onOpenTour = dropUnlessResumed(block = nav.openTour),
                onOpenHowTos = dropUnlessResumed(block = howTo.openList),
            ),
        )
    }
    composable<DataBackupRoute> {
        val howTo = LocalHowTo.current
        settings.dataBackup(
            DataBackupHost(
                onBack = dropUnlessResumed(block = nav.back),
                onImported = nav.backupImported,
                alertKey = nav.alertKey(),
                onOpenHelp = howTo.topic(HowToTopic.DataAndBackup),
            ),
        )
    }
    composable<AboutRoute> {
        settings.about(
            AboutHost(
                onBack = dropUnlessResumed(block = nav.back),
                onRerunSetup = nav.rerunSetup,
                onOpenDiagnostics = { nav.open(DiagnosticsRoute) },
            ),
        )
    }
    composable<DiagnosticsRoute> { settings.diagnostics(dropUnlessResumed(block = nav.back)) }
    composable<TeawareRoute> { entry ->
        settings.teaware(
            TeawareHost(
                onBack = dropUnlessResumed(block = nav.back),
                onOpenVessel = if (nav.openVesselResumedOnly) resumedOnly(nav.openVessel) else nav.openVessel,
                onAddVessel = dropUnlessResumed(block = nav.addVessel),
                archived = entry.archivedVessel(),
                onArchivedSnackbarEnd = entry::forgetArchivedVessel,
            ),
        )
    }
    composable<VesselEditorRoute> {
        settings.vesselEditor(
            VesselEditorHost(
                onClose = dropUnlessResumed(block = nav.back),
                onSave = { id -> nav.closeVesselEditor(id, null) },
                onArchive = { archived -> nav.closeVesselEditor(null, archived) },
                alertKey = nav.alertKey(),
            ),
        )
    }
}
