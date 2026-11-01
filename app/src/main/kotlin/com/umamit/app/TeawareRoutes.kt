package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.umamit.feature.teaware.ArchivedVessel
import com.umamit.feature.teaware.VesselEditorResult

/**
 * Closes the vessel editor on top (SCR-14) and leaves its result in the entry below (the editor's contract,
 * [VesselEditorResult]): [savedVesselId] after *Speichern*, [archived] after *Archivieren*. Once only: a second
 * call, with the editor already gone, does nothing.
 */
internal fun NavController.closeVesselEditor(savedVesselId: String? = null, archived: ArchivedVessel? = null) {
    if (currentDestination?.hasRoute<VesselEditorRoute>() != true) return
    previousBackStackEntry?.savedStateHandle?.let { below ->
        savedVesselId?.let { below[VesselEditorResult.SAVED_VESSEL_ID] = it }
        archived?.let {
            below[VesselEditorResult.ARCHIVED_VESSEL_ID] = it.id
            below[VesselEditorResult.ARCHIVED_VESSEL_NAME] = it.name
        }
    }
    popBackStack()
}

/** The vessel the editor above this entry just archived, until [forgetArchivedVessel]. */
@Composable
internal fun NavBackStackEntry.archivedVessel(): ArchivedVessel? {
    val id by savedStateHandle.getStateFlow<String?>(VesselEditorResult.ARCHIVED_VESSEL_ID, null)
        .collectAsStateWithLifecycle()
    val name by savedStateHandle.getStateFlow<String?>(VesselEditorResult.ARCHIVED_VESSEL_NAME, null)
        .collectAsStateWithLifecycle()
    return id?.let { ArchivedVessel(it, name.orEmpty()) }
}

/** The list's snackbar for the archived vessel is gone. */
internal fun NavBackStackEntry.forgetArchivedVessel() {
    savedStateHandle.remove<String>(VesselEditorResult.ARCHIVED_VESSEL_ID)
    savedStateHandle.remove<String>(VesselEditorResult.ARCHIVED_VESSEL_NAME)
}

/**
 * The vessel the editor above this entry just saved (the setup's *Neues Gefäß …*, WARE-02), until
 * [forgetSavedVessel].
 */
@Composable
internal fun NavBackStackEntry.savedVesselId(): String? {
    val id by savedStateHandle.getStateFlow<String?>(VesselEditorResult.SAVED_VESSEL_ID, null)
        .collectAsStateWithLifecycle()
    return id
}

/** The setup has selected the saved vessel. */
internal fun NavBackStackEntry.forgetSavedVessel() {
    savedStateHandle.remove<String>(VesselEditorResult.SAVED_VESSEL_ID)
}
