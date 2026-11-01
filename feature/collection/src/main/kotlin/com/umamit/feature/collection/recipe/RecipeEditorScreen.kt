package com.umamit.feature.collection.recipe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.DiscardChangesDialog
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.collection.R

/** Test tags of SCR-12. */
object RecipeEditorTags {
    const val NAME = "RecipeEditorName"
    const val METHOD = "RecipeEditorMethod"
    const val VESSEL = "RecipeEditorVessel"
    const val LEAF = "RecipeEditorLeaf"
    const val WATER = "RecipeEditorWater"
    const val RATIO = "RecipeEditorRatio"
    const val TEMPERATURE = "RecipeEditorTemperature"
    const val PRE_ALERT = "RecipeEditorPreAlert"
    const val DEFAULT = "RecipeEditorDefault"
    const val STEPS = "RecipeEditorSteps"
    const val NOTES = "RecipeEditorNotes"
    const val SAVE = "RecipeEditorSave"
    const val MORE = "RecipeEditorMore"
}

/** Test tags of SCR-12's steps; each with the step's number (1-based). */
object RecipeStepTags {
    fun step(number: Int) = "RecipeStep$number"

    fun handle(number: Int) = "RecipeStepHandle$number"

    fun kind(number: Int) = "RecipeStepKind$number"

    fun preAlert(number: Int) = "RecipeStepPreAlert$number"

    fun note(number: Int) = "RecipeStepNote$number"

    const val ADD = "RecipeStepAdd"
}

/**
 * Rezept bearbeiten (SCR-12) with its view model: [onSave] after *Speichern*, [onDelete] after *Löschen*, [onClose]
 * for ✕ and back (after „Änderungen verwerfen?" when something changed, GEN-08).
 */
@Composable
fun RecipeEditorRoute(
    onClose: () -> Unit,
    onSave: (recipeId: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecipeEditorEvent.Saved -> currentOnSave(event.recipeId)
                RecipeEditorEvent.Deleted -> currentOnDelete()
            }
        }
    }
    RecipeEditorScreen(
        state = state,
        actions = RecipeEditorActions(
            update = viewModel::update,
            selectVessel = viewModel::selectVessel,
            save = viewModel::save,
            delete = viewModel::delete,
            messageShown = viewModel::messageShown,
            close = onClose,
        ),
        modifier = modifier,
    )
}

/** What SCR-12's controls do. */
class RecipeEditorActions(
    val update: ((RecipeForm) -> RecipeForm) -> Unit = {},
    val selectVessel: (vesselId: String?) -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
    val messageShown: () -> Unit = {},
    /** Leaves the editor without saving. */
    val close: () -> Unit = {},
)

/**
 * SCR-12 without state, a full-screen dialog like the tea and vessel editors (✕ · title · *Speichern* · ⋮ for a
 * stored recipe): *Rezept* (name, method, vessel, leaf, water and the live ratio, temperature, Vorwarnung, *Als
 * Standard*), *Schritte* and *Notizen*. No series generator (PRF-05 not planned).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(state: RecipeEditorUiState, actions: RecipeEditorActions, modifier: Modifier = Modifier) {
    val ready = state as? RecipeEditorUiState.Ready
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (ready?.hasChanges == true) askDiscard = true else actions.close() }
    BackHandler(enabled = ready?.hasChanges == true) { askDiscard = true }
    val snackbar = remember { SnackbarHostState() }
    val limit = stringResource(R.string.recipe_editor_limit_reached)
    val last = stringResource(R.string.recipe_editor_delete_last)
    LaunchedEffect(ready?.message) {
        val text = when (ready?.message) {
            RecipeEditorMessage.LimitReached -> limit
            RecipeEditorMessage.LastRecipe -> last
            null -> return@LaunchedEffect
        }
        actions.messageShown()
        snackbar.showSnackbar(text)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (ready?.isNew == true) {
                        R.string.recipe_editor_title_new
                    } else {
                        R.string.recipe_editor_title_edit
                    }
                    // One line beside ✕, Speichern and ⋮, even at font scale 2.0 on a short screen.
                    Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(painterResource(UmamiIcons.Close), stringResource(R.string.recipe_editor_close))
                    }
                },
                actions = {
                    if (ready != null) {
                        TextButton(
                            onClick = actions.save,
                            enabled = !ready.saving,
                            modifier = Modifier.testTag(RecipeEditorTags.SAVE),
                        ) {
                            Text(stringResource(R.string.recipe_editor_save))
                        }
                        if (!ready.isNew) EditorMenu(ready.canDelete, onDelete = { askDelete = true })
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
            RecipeEditorUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))

            RecipeEditorUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(UmamiSpacing.SideMargin),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.recipe_editor_not_found), style = MaterialTheme.typography.bodyLarge)
            }

            is RecipeEditorUiState.Ready -> RecipeFields(
                state = state,
                actions = actions,
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
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(R.string.recipe_editor_delete_title)) },
            text = { Text(stringResource(R.string.recipe_editor_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDelete = false
                        actions.delete()
                    },
                ) { Text(stringResource(R.string.recipe_editor_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) {
                    Text(stringResource(R.string.recipe_editor_delete_cancel))
                }
            },
        )
    }
}

/** ⋮: *Rezept löschen*, greyed out for the tea's last recipe (PRF-01). */
@Composable
private fun EditorMenu(canDelete: Boolean, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(RecipeEditorTags.MORE)) {
            Icon(painterResource(UmamiIcons.MoreVert), stringResource(R.string.recipe_editor_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.recipe_editor_delete))
                        if (!canDelete) {
                            Text(
                                text = stringResource(R.string.recipe_editor_delete_last),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                onClick = {
                    open = false
                    onDelete()
                },
                enabled = canDelete,
            )
        }
    }
}
