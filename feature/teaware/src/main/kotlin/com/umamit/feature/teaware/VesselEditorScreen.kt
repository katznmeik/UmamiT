package com.umamit.feature.teaware

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType

/** Test tags of SCR-14. */
object VesselEditorTags {
    const val NAME = "VesselEditorName"
    const val TYPE = "VesselEditorType"
    const val CAPACITY = "VesselEditorCapacity"
    const val USABLE = "VesselEditorUsable"
    const val MATERIAL = "VesselEditorMaterial"
    const val NOTES = "VesselEditorNotes"
    const val FAVORITE = "VesselEditorFavorite"
    const val SAVE = "VesselEditorSave"
    const val MORE = "VesselEditorMore"
}

/**
 * The editor's result contract for its host (SCR-14). The app opens the editor as a route of its own
 * (`VesselEditorRoute(vesselId)`, a new vessel without an id) and, when it closes after *Speichern*,
 * puts the saved vessel's id under [SAVED_VESSEL_ID] into the `SavedStateHandle` of the back-stack
 * entry below it; the setup's *Neues Gefäß …* (WARE-02, D-100) reads it there and selects the vessel.
 * After *Archivieren*, the entry below gets [ARCHIVED_VESSEL_ID] and [ARCHIVED_VESSEL_NAME] (the list's
 * snackbar). ✕ and back leave no result.
 */
object VesselEditorResult {
    const val SAVED_VESSEL_ID = "teaware.savedVesselId"
    const val ARCHIVED_VESSEL_ID = "teaware.archivedVesselId"
    const val ARCHIVED_VESSEL_NAME = "teaware.archivedVesselName"
}

/**
 * Gefäß bearbeiten (SCR-14) with its view model: [onSaved] with the stored vessel's id after
 * *Speichern*, [onArchived] after *Archivieren*, [onClose] for ✕ and back (after „Änderungen
 * verwerfen?" when something changed, GEN-08).
 */
@Composable
fun VesselEditorRoute(
    onClose: () -> Unit,
    onSaved: (vesselId: String) -> Unit,
    modifier: Modifier = Modifier,
    onArchived: (ArchivedVessel) -> Unit = {},
    viewModel: VesselEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnArchived by rememberUpdatedState(onArchived)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VesselEditorEvent.Saved -> currentOnSaved(event.vesselId)
                is VesselEditorEvent.Archived -> currentOnArchived(ArchivedVessel(event.vesselId, event.name))
            }
        }
    }
    VesselEditorScreen(
        state = state,
        actions = VesselEditorActions(
            update = viewModel::update,
            save = viewModel::save,
            archive = viewModel::archive,
            restore = viewModel::restore,
            close = onClose,
        ),
        modifier = modifier,
    )
}

/** What SCR-14's controls do. */
class VesselEditorActions(
    val update: ((VesselForm) -> VesselForm) -> Unit = {},
    val save: () -> Unit = {},
    val archive: () -> Unit = {},
    val restore: () -> Unit = {},
    /** Leaves the editor without saving. */
    val close: () -> Unit = {},
)

/**
 * SCR-14 without state, a full-screen dialog like SCR-10: ✕ · title · *Speichern* (and ⋮ for a stored
 * vessel), then name, type, volume, usable volume, material, notes and *Favorit*. No photo (v0.4.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VesselEditorScreen(state: VesselEditorUiState, actions: VesselEditorActions, modifier: Modifier = Modifier) {
    val ready = state as? VesselEditorUiState.Ready
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (ready?.hasChanges == true) askDiscard = true else actions.close() }
    BackHandler(enabled = ready?.hasChanges == true) { askDiscard = true }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (ready?.isNew == true) {
                        R.string.vessel_editor_title_new
                    } else {
                        R.string.vessel_editor_title_edit
                    }
                    // One line beside ✕, Speichern and ⋮, even at font scale 2.0 on a short screen.
                    Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(painterResource(UmamiIcons.Close), stringResource(R.string.vessel_editor_close))
                    }
                },
                actions = {
                    if (ready != null) {
                        TextButton(onClick = actions.save, modifier = Modifier.testTag(VesselEditorTags.SAVE)) {
                            Text(stringResource(R.string.vessel_editor_save))
                        }
                        if (!ready.isNew) EditorMenu(ready, actions)
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        when (state) {
            VesselEditorUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            VesselEditorUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(UmamiSpacing.SideMargin),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.vessel_editor_not_found), style = MaterialTheme.typography.bodyLarge)
            }

            is VesselEditorUiState.Ready -> EditorFields(
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

/** ⋮: *Archivieren* (greyed out for the active brew session's vessel, E10) or *Wiederherstellen*. */
@Composable
private fun EditorMenu(state: VesselEditorUiState.Ready, actions: VesselEditorActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(VesselEditorTags.MORE)) {
            Icon(painterResource(UmamiIcons.MoreVert), stringResource(R.string.vessel_editor_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.isArchived) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vessel_editor_restore)) },
                    onClick = {
                        open = false
                        actions.restore()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.vessel_editor_archive))
                            if (state.archiveBlocked) {
                                Text(
                                    text = stringResource(R.string.vessel_editor_archive_blocked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        open = false
                        actions.archive()
                    },
                    enabled = !state.archiveBlocked,
                )
            }
        }
    }
}

@Composable
private fun EditorFields(
    state: VesselEditorUiState.Ready,
    update: ((VesselForm) -> VesselForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = modifier.padding(vertical = UmamiSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin)
        if (state.isArchived) {
            Text(
                text = stringResource(R.string.vessel_editor_archived_note),
                modifier = fieldModifier,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val nameError = when {
            VesselFieldError.NameEmpty in errors -> R.string.vessel_editor_error_name_empty
            VesselFieldError.NameTooLong in errors -> R.string.vessel_editor_error_name_long
            else -> null
        }
        OutlinedTextField(
            value = form.name,
            onValueChange = { value -> update { it.copy(name = value) } },
            modifier = fieldModifier.testTag(VesselEditorTags.NAME),
            label = { Text(stringResource(R.string.vessel_editor_name)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        ChoiceField(
            label = stringResource(R.string.vessel_editor_type),
            selected = form.type,
            options = VesselType.entries,
            optionLabel = { stringResource(it.label()) },
            optionIcon = { it.icon() },
            onSelect = { type -> update { it.copy(type = type) } },
            modifier = fieldModifier.testTag(VesselEditorTags.TYPE),
        )
        VolumeField(
            value = form.capacity,
            label = stringResource(R.string.vessel_editor_capacity),
            error = (VesselFieldError.CapacityRange in errors).then(R.string.vessel_editor_error_capacity),
            onValueChange = { value -> update { it.copy(capacity = value) } },
            modifier = fieldModifier.testTag(VesselEditorTags.CAPACITY),
        )
        VolumeField(
            value = form.usable,
            label = stringResource(R.string.vessel_editor_usable),
            error = (VesselFieldError.UsableRange in errors).then(R.string.vessel_editor_error_usable),
            hint = stringResource(R.string.vessel_editor_usable_hint),
            onValueChange = { value -> update { it.copy(usable = value) } },
            modifier = fieldModifier.testTag(VesselEditorTags.USABLE),
        )
        ChoiceField(
            label = stringResource(R.string.vessel_editor_material),
            selected = form.material,
            options = listOf<VesselMaterial?>(null) + VesselMaterial.entries,
            optionLabel = { stringResource(it?.label() ?: R.string.vessel_editor_material_none) },
            onSelect = { material -> update { it.copy(material = material) } },
            modifier = fieldModifier.testTag(VesselEditorTags.MATERIAL),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { value -> update { it.copy(notes = value) } },
            modifier = fieldModifier.testTag(VesselEditorTags.NOTES),
            label = { Text(stringResource(R.string.vessel_editor_notes)) },
            minLines = NOTES_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.vessel_editor_favorite)) },
            supportingContent = { Text(stringResource(R.string.vessel_editor_favorite_detail)) },
            // The row is the switch: one node, one target.
            trailingContent = { Switch(checked = form.isFavorite, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VesselEditorTags.FAVORITE)
                .toggleable(
                    value = form.isFavorite,
                    role = Role.Switch,
                    onValueChange = { on -> update { it.copy(isFavorite = on) } },
                )
                .padding(horizontal = UmamiSpacing.XXSmall),
        )
    }
}

@Composable
private fun VolumeField(
    value: String,
    label: String,
    error: Int?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        // Digits only: a volume is whole millilitres.
        onValueChange = { typed -> onValueChange(typed.filter(Char::isDigit).take(MAX_VOLUME_DIGITS)) },
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.vessel_editor_ml)) },
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> {
                { Text(stringResource(error)) }
            }

            hint != null -> {
                { Text(hint) }
            }

            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** A read-only field that opens a menu of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionIcon: ((T) -> Int)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = optionIcon?.let { icon -> { OptionIcon(icon(selected)) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = optionIcon?.let { icon -> { OptionIcon(icon(option)) } },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun OptionIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Boolean.then(value: Int): Int? = if (this) value else null

private const val NOTES_MIN_LINES = 3

/** „3000" has four digits; more is never valid. */
private const val MAX_VOLUME_DIGITS = 4
