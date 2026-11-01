package com.umamit.feature.collection.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.DiscardChangesDialog
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.UmamiLevel
import com.umamit.feature.collection.R
import com.umamit.feature.collection.detail.label
import com.umamit.feature.collection.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tags of SCR-10. */
object TeaEditorTags {
    const val NAME = "TeaEditorName"
    const val SECONDARY_NAME = "TeaEditorSecondaryName"
    const val SORTE = "TeaEditorSorte"
    const val UMAMI = "TeaEditorUmami"
    const val FLAVORED = "TeaEditorFlavored"
    const val COUNTRY = "TeaEditorCountry"
    const val REGION = "TeaEditorRegion"
    const val CULTIVAR = "TeaEditorCultivar"
    const val HARVEST_YEAR = "TeaEditorHarvestYear"
    const val HARVEST_SEASON = "TeaEditorHarvestSeason"
    const val SHADING = "TeaEditorShading"
    const val PROCESSING = "TeaEditorProcessing"
    const val VENDOR = "TeaEditorVendor"
    const val PURCHASE_DATE = "TeaEditorPurchaseDate"
    const val PACKAGE = "TeaEditorPackage"
    const val PRICE = "TeaEditorPrice"
    const val NOTES = "TeaEditorNotes"
    const val SAVE = "TeaEditorSave"
}

/**
 * Tee bearbeiten (SCR-10) with its view model: [onSave] with the stored tea's id after *Speichern* (after an add,
 * [isNew][TeaEditorEvent.Saved.isNew]: the host opens its detail), [onClose] for ✕ and back (after „Änderungen
 * verwerfen?" when something changed, GEN-08).
 */
@Composable
fun TeaEditorRoute(
    onClose: () -> Unit,
    onSave: (teaId: String, isNew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeaEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TeaEditorEvent.Saved -> currentOnSave(event.teaId, event.isNew)
            }
        }
    }
    TeaEditorScreen(
        state = state,
        actions = TeaEditorActions(update = viewModel::update, save = viewModel::save, close = onClose),
        modifier = modifier,
    )
}

/** What SCR-10's controls do. */
class TeaEditorActions(
    val update: ((TeaEditorForm) -> TeaEditorForm) -> Unit = {},
    val save: () -> Unit = {},
    /** Leaves the editor without saving. */
    val close: () -> Unit = {},
)

/**
 * SCR-10 without state, a full-screen dialog (✕ · title · *Speichern*) like the vessel editor (SCR-14): *Allgemein*,
 * *Herkunft*, *Kauf* (the package only when a tea is added) and *Notizen*. No photos, tags or freshness dates
 * (v0.4.1, D-100).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaEditorScreen(state: TeaEditorUiState, actions: TeaEditorActions, modifier: Modifier = Modifier) {
    val ready = state as? TeaEditorUiState.Ready
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (ready?.hasChanges == true) askDiscard = true else actions.close() }
    BackHandler(enabled = ready?.hasChanges == true) { askDiscard = true }
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.tea_editor_save_failed)
    LaunchedEffect(ready?.saveFailed) { if (ready?.saveFailed == true) snackbar.showSnackbar(failed) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (ready?.isNew ==
                        true
                    ) {
                        R.string.tea_editor_title_new
                    } else {
                        R.string.tea_editor_title_edit
                    }
                    // One line beside ✕ and Speichern, even at font scale 2.0 on a short screen.
                    Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(painterResource(UmamiIcons.Close), stringResource(R.string.tea_editor_close))
                    }
                },
                actions = {
                    if (ready != null) {
                        TextButton(
                            onClick = actions.save,
                            enabled = !ready.saving,
                            modifier = Modifier.testTag(TeaEditorTags.SAVE),
                        ) {
                            Text(stringResource(R.string.tea_editor_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        when (state) {
            TeaEditorUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            TeaEditorUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(UmamiSpacing.SideMargin),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tea_editor_not_found), style = MaterialTheme.typography.bodyLarge)
            }

            is TeaEditorUiState.Ready -> EditorFields(
                state = state,
                update = actions.update,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
    if (askDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { askDiscard = false },
            onDiscard = {
                askDiscard = false
                actions.close()
            },
        )
    }
}
