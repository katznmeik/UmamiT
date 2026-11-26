package com.umamit.feature.collection.recipe

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.EditorScaffold
import com.umamit.core.ui.HelpOverflowAction
import com.umamit.core.ui.fadeInOnArrival
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.collection.R
import com.umamit.core.designsystem.R as DsR

/** Test tags of SCR-12. */
object RecipeEditorTags {
    const val HELP = "RecipeEditorHelp"
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
    const val SHARE = "RecipeEditorShare"
    const val DELETE = "RecipeEditorDelete"
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
    alertKey: String? = null,
    onOpenHelp: () -> Unit = {},
    viewModel: RecipeEditorViewModel = hiltViewModel(),
    sharer: RecipeSharer = rememberRecipeSharer(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecipeEditorEvent.Saved -> currentOnSave(event.recipeId)
                RecipeEditorEvent.Deleted -> currentOnDelete()
                is RecipeEditorEvent.Share -> if (!context.share(sharer, event)) viewModel.shareFailed()
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
            share = viewModel::share,
            messageShown = viewModel::messageShown,
            openHelp = onOpenHelp,
            close = onClose,
        ),
        modifier = modifier,
        alertKey = alertKey,
    )
}

/** What SCR-12's controls do. */
class RecipeEditorActions(
    val update: ((RecipeForm) -> RecipeForm) -> Unit = {},
    val selectVessel: (vesselId: String?) -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
    /** ⋮ *Rezept teilen* (PRF-08). */
    val share: () -> Unit = {},
    val messageShown: () -> Unit = {},
    /** ⋮ *Hilfe* (SCR-36 „Entry points"): the Anleitung *Rezepte*. */
    val openHelp: () -> Unit = {},
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
fun RecipeEditorScreen(
    state: RecipeEditorUiState,
    actions: RecipeEditorActions,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
) {
    val ready = state as? RecipeEditorUiState.Ready
    val askDelete = rememberAlertGate(alertKey)
    val snackbar = remember { SnackbarHostState() }
    val limit = stringResource(R.string.recipe_editor_limit_reached)
    val last = stringResource(R.string.recipe_editor_delete_last)
    val brewing = stringResource(R.string.recipe_editor_delete_brewing)
    val shareFailed = stringResource(R.string.recipe_editor_share_failed)
    LaunchedEffect(ready?.message) {
        val text = when (ready?.message) {
            RecipeEditorMessage.LimitReached -> limit
            RecipeEditorMessage.LastRecipe -> last
            RecipeEditorMessage.InActiveSession -> brewing
            RecipeEditorMessage.ShareFailed -> shareFailed
            null -> return@LaunchedEffect
        }
        actions.messageShown()
        snackbar.showSnackbar(text)
    }
    EditorScaffold(
        title = stringResource(
            if (ready?.isNew == true) R.string.recipe_editor_title_new else R.string.recipe_editor_title_edit,
        ),
        closeDescription = stringResource(R.string.recipe_editor_close),
        saveLabel = stringResource(R.string.recipe_editor_save),
        saveTag = RecipeEditorTags.SAVE,
        showActions = ready != null,
        hasChanges = ready?.hasChanges == true,
        saving = ready?.saving == true,
        onSave = actions.save,
        onClose = actions.close,
        modifier = modifier,
        alertKey = alertKey,
        snackbarHost = { SnackbarHost(snackbar) },
        menu = {
            if (ready != null && !ready.isNew) {
                EditorMenu(
                    ready,
                    alertKey,
                    onShare = actions.share,
                    onDelete = askDelete::open,
                    onHelp = actions.openHelp,
                )
            } else if (ready != null) {
                // A new recipe has nothing to share or delete: its ⋮ holds only *Hilfe* (SCR-36 „Entry points").
                HelpOverflowAction(actions.openHelp, alertKey)
            }
        },
    ) { padding ->
        // The first arrival of the data fades in instead of popping in part-way through the enter (A-12).
        Box(Modifier.fillMaxSize().fadeInOnArrival(state != RecipeEditorUiState.Loading)) {
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
                    alertKey = alertKey,
                    // The padding holds the keyboard too (safeDrawing): no imePadding on top, which hid the fields
                    // behind it (D-102).
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
    AlertGated(askDelete) {
        AlertDialog(
            onDismissRequest = askDelete::close,
            title = { Text(stringResource(R.string.recipe_editor_delete_title)) },
            text = { Text(stringResource(R.string.recipe_editor_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDelete.close()
                        actions.delete()
                    },
                ) { Text(stringResource(R.string.recipe_editor_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = askDelete::close) {
                    Text(stringResource(R.string.recipe_editor_delete_cancel))
                }
            },
        )
    }
}

/**
 * ⋮: *Rezept teilen* (PRF-08), greyed out with „Erst speichern" while the editor has changes; *Rezept löschen*,
 * greyed out for the tea's last recipe (PRF-01) and the running brew's (E15, D-101 F12).
 */
@Composable
private fun EditorMenu(
    state: RecipeEditorUiState.Ready,
    alertKey: String?,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onHelp: () -> Unit,
) {
    val menu = rememberAlertGate(alertKey)
    Box {
        IconButton(onClick = menu::open, modifier = Modifier.testTag(RecipeEditorTags.MORE)) {
            Icon(painterResource(UmamiIcons.MoreVert), stringResource(R.string.recipe_editor_more))
        }
        AlertGatedDropdownMenu(menu) {
            MenuEntry(
                label = R.string.recipe_editor_share,
                reason = R.string.recipe_editor_share_unsaved.takeUnless { state.canShare },
                tag = RecipeEditorTags.SHARE,
                onClick = {
                    menu.close()
                    onShare()
                },
            )
            MenuEntry(
                label = R.string.recipe_editor_delete,
                reason = when {
                    state.canDelete -> null
                    state.isBrewing -> R.string.recipe_editor_delete_brewing
                    else -> R.string.recipe_editor_delete_last
                },
                tag = RecipeEditorTags.DELETE,
                onClick = {
                    menu.close()
                    onDelete()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(DsR.string.ds_help)) },
                onClick = {
                    menu.close()
                    onHelp()
                },
                modifier = Modifier.testTag(RecipeEditorTags.HELP),
            )
        }
    }
}

/** One ⋮ item; with a [reason] it is greyed out and says why below its [label]. */
@Composable
private fun MenuEntry(@StringRes label: Int, @StringRes reason: Int?, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(label))
                if (reason != null) {
                    Text(
                        text = stringResource(reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
        enabled = reason == null,
        modifier = Modifier.testTag(tag),
    )
}
