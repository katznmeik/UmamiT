package com.umamit.feature.brew.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.rememberPhotoPainter
import com.umamit.feature.brew.R
import kotlinx.coroutines.withTimeoutOrNull

const val TEA_PICKER_TAG = "tea_picker"

/**
 * The tea picker (SCR-03) with its view model, as Material 3's modal bottom
 * sheet: one tap on a tea opens its setup ([onPickTea]); *Aus dem Katalog*
 * leads through the catalog to the add sheet and back to the setup of the new
 * tea ([onCatalog], D-073); *Ohne Tee (Schnelltimer)* the quick timer
 * ([onQuickTimer]).
 *
 * **Alerts first (rule 8):** the host remembers the alert the sheet was
 * opened in ([openedIn], the `alertKey` of that moment). While another alert
 * plays ([alertKey]) the sheet is not composed, so it leaves in the same
 * frame without animation, and [onDismissRequest] is called.
 */
@Composable
fun TeaPickerHost(
    open: Boolean,
    openedIn: String?,
    alertKey: String?,
    onPickTea: (teaId: String) -> Unit,
    onQuickTimer: () -> Unit,
    onDismissRequest: () -> Unit,
    onCatalog: () -> Unit = {},
    viewModel: TeaPickerViewModel = hiltViewModel(),
) {
    // A closed picker forgets its search and its undo, so it opens on the sections again.
    LaunchedEffect(open, viewModel) {
        if (!open) {
            viewModel.setQuery("")
            viewModel.dismissRemoved()
        }
    }
    AlertGate(open, openedIn, alertKey, onDismissRequest) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        TeaPickerSheet(
            state = state,
            onQueryChange = viewModel::setQuery,
            onPickTea = onPickTea,
            onQuickTimer = onQuickTimer,
            onDismissRequest = onDismissRequest,
            onCatalog = onCatalog,
            onRemoveTea = viewModel::remove,
            onUndoRemove = viewModel::undoRemove,
            onRemovedDismiss = viewModel::dismissRemoved,
        )
    }
}

/**
 * Composes [content] while [open], unless an alert plays that it was not
 * opened in ([openedIn]): then it is removed in that frame (no exit
 * animation can cover the alert) and [onDismissRequest] closes it for good
 * (rule 8, D-058).
 */
@Composable
internal fun AlertGate(
    open: Boolean,
    openedIn: String?,
    alertKey: String?,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val coveredAlert = alertKey != null && alertKey != openedIn
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    LaunchedEffect(open, coveredAlert) { if (open && coveredAlert) currentOnDismiss() }
    if (open && !coveredAlert) content()
}

/**
 * The stateless tea picker sheet (SCR-03): search, *Zuletzt*, *Favoriten*,
 * *Alle* and the footer *Aus dem Katalog* and *Ohne Tee (Schnelltimer)*. It opens expanded with
 * Material's calm sheet motion, like the Schritte-Sheet (D-051); under reduce
 * motion it appears without the slide. A pick calls back in the tap's frame:
 * the host closes the sheet and opens the next route, whose fade is the only
 * transition (no sheet exit before it), and nothing can navigate after an
 * alert has started (rule 8, D-062).
 *
 * A long press on a tea opens its menu with *Aus Sammlung entfernen* ([onRemoveTea]; greyed out
 * with „Erst die Sitzung beenden" for the active session's tea, E15). While [TeaPickerUiState.removed]
 * is set, a snackbar of about 8 s offers *Rückgängig* ([onUndoRemove]); when it goes,
 * [onRemovedDismiss] is called (TEA-04, D-093 Q21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaPickerSheet(
    state: TeaPickerUiState,
    onQueryChange: (String) -> Unit,
    onPickTea: (teaId: String) -> Unit,
    onQuickTimer: () -> Unit,
    onDismissRequest: () -> Unit,
    onCatalog: () -> Unit = {},
    onRemoveTea: (teaId: String) -> Unit = {},
    onUndoRemove: () -> Unit = {},
    onRemovedDismiss: () -> Unit = {},
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberPickerSheetState(reduceMotion)
    val snackbarHostState = remember { SnackbarHostState() }
    RemovedSnackbar(state.removed, snackbarHostState, onUndoRemove, onRemovedDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(TEA_PICKER_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // The snackbar overlays the end of the sheet, above the navigation bar.
        Box {
            PickerContent(state, onQueryChange, onPickTea, onQuickTimer, onCatalog, onRemoveTea)
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * Shows the snackbar „*Tanabata* aus der Sammlung entfernt" · *Rückgängig* for [removed]: about
 * 8 s (D-093 Q21), longer when the accessibility settings ask for more time.
 */
@Composable
private fun RemovedSnackbar(
    removed: PickerTea?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = removed?.let { stringResource(R.string.brew_picker_removed, it.name) }
    val undo = stringResource(R.string.brew_picker_undo)
    val accessibility = LocalAccessibilityManager.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(removed) {
        if (message == null) return@LaunchedEffect
        val timeoutMs = accessibility?.calculateRecommendedTimeoutMillis(
            UNDO_TIMEOUT_MS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_TIMEOUT_MS
        val result = withTimeoutOrNull(timeoutMs) {
            hostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) currentOnUndo() else currentOnDismiss()
    }
}

@Composable
private fun PickerContent(
    state: TeaPickerUiState,
    onQueryChange: (String) -> Unit,
    onPickTea: (teaId: String) -> Unit,
    onQuickTimer: () -> Unit,
    onCatalog: () -> Unit,
    onRemoveTea: (teaId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.brew_picker_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = UmamiSpacing.SideMargin)
                .semantics { heading() },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.brew_picker_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        )
        PickerLists(state, TeaRowActions(pick = onPickTea, remove = onRemoveTea))
        HorizontalDivider(
            modifier = Modifier.padding(top = UmamiSpacing.Small),
            thickness = UmamiSpacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedButton(
            onClick = onCatalog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin)
                .padding(top = UmamiSpacing.Medium),
        ) {
            Icon(painterResource(UmamiIcons.Collection), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(UmamiSpacing.XSmall))
            Text(stringResource(R.string.brew_picker_catalog))
        }
        OutlinedButton(
            onClick = onQuickTimer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)
                .padding(bottom = UmamiSpacing.XSmall),
        ) {
            Icon(painterResource(UmamiIcons.Timer), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(UmamiSpacing.XSmall))
            Text(stringResource(R.string.brew_picker_quick_timer))
        }
    }
}

/** What a tea row does: a tap picks it, its menu removes it (SCR-03, TEA-04). */
private class TeaRowActions(val pick: (teaId: String) -> Unit, val remove: (teaId: String) -> Unit)

@Composable
private fun PickerLists(state: TeaPickerUiState, actions: TeaRowActions) {
    val results = state.resultGroups
    when {
        results != null -> if (results.isEmpty()) {
            Hint(stringResource(R.string.brew_picker_no_results, state.query.trim()))
        } else if (state.showResultHeaders) {
            results.forEach { Section(it.kind.title(), it.teas, actions) }
        } else {
            Section(null, state.results.orEmpty(), actions)
        }

        state.isEmpty -> Hint(stringResource(R.string.brew_picker_empty))

        else -> {
            Section(stringResource(R.string.brew_picker_recent), state.recent, actions)
            Section(stringResource(R.string.brew_picker_favorites), state.favorites, actions)
            // With more than one kind, *Alle* is told by its groups' headers (D-097).
            if (state.showGroupHeaders) {
                state.allGroups.forEach { Section(it.kind.title(), it.teas, actions) }
            } else {
                Section(stringResource(R.string.brew_picker_all), state.all, actions)
            }
        }
    }
}

/** The header of a kind's group (SCR-03, D-097). */
@Composable
private fun TeaKind.title(): String = stringResource(
    when (this) {
        TeaKind.GREEN -> R.string.brew_picker_group_green
        TeaKind.HERBAL -> R.string.brew_picker_group_herbal
    },
)

@Composable
private fun Section(title: String?, teas: List<PickerTea>, actions: TeaRowActions) {
    if (teas.isEmpty()) return
    if (title != null) SectionHeader(title, Modifier.padding(top = UmamiSpacing.Small))
    teas.forEachIndexed { i, tea ->
        TeaRow(tea, actions, showDivider = i < teas.lastIndex)
    }
}

/**
 * A tea of the picker. A long press opens its menu with *Aus Sammlung entfernen*, greyed out with
 * „Erst die Sitzung beenden" for the active session's tea (E15); TalkBack offers the removal as a
 * custom action on the row (SCR-03).
 */
@Composable
private fun TeaRow(tea: PickerTea, actions: TeaRowActions, showDivider: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.brew_picker_remove)
    Box {
        TeaListItem(
            title = tea.name,
            meta = tea.teaType.orEmpty(),
            onClick = { actions.pick(tea.id) },
            thumbnail = rememberPhotoPainter(tea.photoPath),
            showDivider = showDivider,
            onLongClick = { menuOpen = true },
            onLongClickLabel = stringResource(R.string.brew_picker_tea_menu),
            customActions = if (tea.removable) {
                listOf(
                    CustomAccessibilityAction(removeLabel) {
                        actions.remove(tea.id)
                        true
                    },
                )
            } else {
                emptyList()
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = DpOffset(x = MenuInset, y = 0.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(removeLabel)
                        if (!tea.removable) {
                            Text(
                                text = stringResource(R.string.brew_picker_remove_blocked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                onClick = {
                    menuOpen = false
                    actions.remove(tea.id)
                },
                enabled = tea.removable,
            )
        }
    }
}

/** The menu opens under the tea's name, past its thumbnail (03 §3.8). */
private val MenuInset = UmamiSpacing.SideMargin + 48.dp + UmamiSpacing.Medium

/** About 8 s for *Rückgängig* (D-093 Q21). */
private const val UNDO_TIMEOUT_MS = 8_000L

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = UmamiSpacing.SideMargin,
            end = UmamiSpacing.SideMargin,
            top = UmamiSpacing.Large,
            bottom = UmamiSpacing.Large + UmamiSpacing.Small,
        ),
    )
}

/** Always fully expanded; under reduce motion it starts expanded, so it appears without the slide (03 §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberPickerSheetState(reduceMotion: Boolean): SheetState {
    val density = LocalDensity.current
    return remember(reduceMotion) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
