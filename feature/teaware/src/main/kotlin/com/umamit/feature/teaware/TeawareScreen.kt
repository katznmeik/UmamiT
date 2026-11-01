package com.umamit.feature.teaware

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import kotlinx.coroutines.withTimeoutOrNull

/** Test tags of SCR-13. */
object TeawareTags {
    const val LIST = "TeawareList"
    const val ADD = "TeawareAdd"
}

/** A vessel the editor just archived, for the list's „*[name]* archiviert" · *Rückgängig* (D-100). */
data class ArchivedVessel(val id: String, val name: String)

/**
 * Teegeschirr (SCR-13) with its view model, from *Einstellungen › Teegeschirr* (SET-30).
 *
 * @param archived the vessel the editor above just archived, or null; [onArchivedSnackbarEnd] once its
 *   snackbar is gone (undone or not).
 */
@Composable
fun TeawareRoute(
    onBack: () -> Unit,
    onOpenVessel: (vesselId: String) -> Unit,
    onAddVessel: () -> Unit,
    modifier: Modifier = Modifier,
    archived: ArchivedVessel? = null,
    onArchivedSnackbarEnd: () -> Unit = {},
    viewModel: TeawareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TeawareScreen(
        state = state,
        onBack = onBack,
        onOpenVessel = onOpenVessel,
        onAddVessel = onAddVessel,
        onRestore = viewModel::restore,
        modifier = modifier,
        archived = archived,
        onUndoArchive = { id ->
            viewModel.restore(id)
            onArchivedSnackbarEnd()
        },
        onArchivedDismiss = onArchivedSnackbarEnd,
    )
}

/**
 * SCR-13 without state: favourites first, then A–Z; archived vessels under „ARCHIVIERT" with
 * *Wiederherstellen*; a FAB adds a vessel (WARE-01, D-100).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeawareScreen(
    state: TeawareUiState,
    onBack: () -> Unit,
    onOpenVessel: (vesselId: String) -> Unit,
    onAddVessel: () -> Unit,
    onRestore: (vesselId: String) -> Unit,
    modifier: Modifier = Modifier,
    archived: ArchivedVessel? = null,
    onUndoArchive: (vesselId: String) -> Unit = {},
    onArchivedDismiss: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ArchivedSnackbar(archived, snackbarHostState, onUndoArchive, onArchivedDismiss)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.teaware_title)) }, navigationIcon = { BackAction(onBack) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVessel, modifier = Modifier.testTag(TeawareTags.ADD)) {
                Icon(painterResource(UmamiIcons.Add), contentDescription = stringResource(R.string.teaware_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        when (state) {
            TeawareUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            is TeawareUiState.Ready -> if (state.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = stringResource(R.string.teaware_empty_title),
                        body = stringResource(R.string.teaware_empty_body),
                    )
                }
            } else {
                VesselList(state, padding, onOpenVessel, onRestore)
            }
        }
    }
}

@Composable
private fun VesselList(
    state: TeawareUiState.Ready,
    padding: PaddingValues,
    onOpenVessel: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag(TeawareTags.LIST),
        // The last row stays clear of the FAB.
        contentPadding = PaddingValues(bottom = FabClearance),
    ) {
        items(state.vessels, key = { it.id }) { vessel ->
            VesselListItem(vessel, onClick = { onOpenVessel(vessel.id) })
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                SectionHeader(
                    title = stringResource(R.string.teaware_section_archived),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UmamiSpacing.Small),
                )
            }
            items(state.archived, key = { "archived-${it.id}" }) { vessel ->
                VesselListItem(vessel, onClick = { onOpenVessel(vessel.id) }, onRestore = { onRestore(vessel.id) })
            }
        }
    }
}

/** A vessel: type icon, name, volume; a favourite's star; *Wiederherstellen* on an archived one. */
@Composable
private fun VesselListItem(vessel: VesselRow, onClick: () -> Unit, onRestore: (() -> Unit)? = null) {
    val volume = vessel.usableMl?.let { stringResource(R.string.teaware_volume_usable, vessel.capacityMl, it) }
        ?: stringResource(R.string.teaware_volume, vessel.capacityMl)
    ListItem(
        headlineContent = { Text(vessel.name) },
        supportingContent = { Text(volume) },
        leadingContent = {
            Icon(
                painter = painterResource(vessel.type.icon()),
                contentDescription = stringResource(vessel.type.label()),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = when {
            onRestore != null -> {
                { TextButton(onClick = onRestore) { Text(stringResource(R.string.teaware_restore)) } }
            }

            vessel.isFavorite -> {
                {
                    Icon(
                        painter = painterResource(UmamiIcons.StarFilled),
                        contentDescription = stringResource(R.string.teaware_favorite),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = UmamiSpacing.XXSmall),
    )
}

/**
 * „*Kyusu 250 ml* archiviert" · *Rückgängig* for [archived]: about 8 s like the picker's (D-093 Q21),
 * longer when the accessibility settings ask for more time.
 */
@Composable
private fun ArchivedSnackbar(
    archived: ArchivedVessel?,
    hostState: SnackbarHostState,
    onUndo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val message = archived?.let { stringResource(R.string.teaware_archived, it.name) }
    val undo = stringResource(R.string.teaware_undo)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(archived) {
        if (archived == null || message == null) return@LaunchedEffect
        val timeoutMs = accessibility?.calculateRecommendedTimeoutMillis(
            UNDO_TIMEOUT_MS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_TIMEOUT_MS
        val result = withTimeoutOrNull(timeoutMs) {
            hostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) currentOnUndo(archived.id) else currentOnDismiss()
    }
}

private const val UNDO_TIMEOUT_MS = 8_000L

/** FAB height plus its margin. */
private val FabClearance = 88.dp
