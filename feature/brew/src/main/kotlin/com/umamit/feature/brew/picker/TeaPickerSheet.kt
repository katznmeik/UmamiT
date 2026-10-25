package com.umamit.feature.brew.picker

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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

const val TEA_PICKER_TAG = "tea_picker"

/**
 * The tea picker (SCR-03) with its view model, as Material 3's modal bottom
 * sheet: one tap on a tea opens its setup ([onPickTea]); *Ohne Tee
 * (Schnelltimer)* the quick timer ([onQuickTimer]). *Aus dem Katalog* comes
 * with M2.
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
    viewModel: TeaPickerViewModel = hiltViewModel(),
) {
    // A closed picker forgets its search, so it opens on the sections again.
    LaunchedEffect(open, viewModel) { if (!open) viewModel.setQuery("") }
    AlertGate(open, openedIn, alertKey, onDismissRequest) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        TeaPickerSheet(
            state = state,
            onQueryChange = viewModel::setQuery,
            onPickTea = onPickTea,
            onQuickTimer = onQuickTimer,
            onDismissRequest = onDismissRequest,
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
 * *Alle* and the footer *Ohne Tee (Schnelltimer)*. It opens expanded with
 * Material's calm sheet motion, like the Schritte-Sheet (D-051); under reduce
 * motion it appears without the slide. A pick calls back in the tap's frame:
 * the host closes the sheet and opens the next route, whose fade is the only
 * transition (no sheet exit before it), and nothing can navigate after an
 * alert has started (rule 8, D-062).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaPickerSheet(
    state: TeaPickerUiState,
    onQueryChange: (String) -> Unit,
    onPickTea: (teaId: String) -> Unit,
    onQuickTimer: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberPickerSheetState(reduceMotion)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(TEA_PICKER_TAG),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
            PickerLists(state, onPickTea)
            HorizontalDivider(
                modifier = Modifier.padding(top = UmamiSpacing.Small),
                thickness = UmamiSpacing.Hairline,
                color = MaterialTheme.colorScheme.outline,
            )
            OutlinedButton(
                onClick = onQuickTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium),
            ) {
                Icon(painterResource(UmamiIcons.Timer), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Text(stringResource(R.string.brew_picker_quick_timer))
            }
        }
    }
}

@Composable
private fun PickerLists(state: TeaPickerUiState, onPick: (String) -> Unit) {
    val results = state.results
    when {
        results != null -> if (results.isEmpty()) {
            Hint(stringResource(R.string.brew_picker_no_results, state.query.trim()))
        } else {
            Section(null, results, onPick)
        }

        state.isEmpty -> Hint(stringResource(R.string.brew_picker_empty))

        else -> {
            Section(stringResource(R.string.brew_picker_recent), state.recent, onPick)
            Section(stringResource(R.string.brew_picker_favorites), state.favorites, onPick)
            Section(stringResource(R.string.brew_picker_all), state.all, onPick)
        }
    }
}

@Composable
private fun Section(title: String?, teas: List<PickerTea>, onPick: (String) -> Unit) {
    if (teas.isEmpty()) return
    if (title != null) SectionHeader(title, Modifier.padding(top = UmamiSpacing.Small))
    teas.forEachIndexed { i, tea ->
        TeaListItem(
            title = tea.name,
            meta = tea.teaType.orEmpty(),
            onClick = { onPick(tea.id) },
            thumbnail = rememberPhotoPainter(tea.photoPath),
            showDivider = i < teas.lastIndex,
        )
    }
}

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
