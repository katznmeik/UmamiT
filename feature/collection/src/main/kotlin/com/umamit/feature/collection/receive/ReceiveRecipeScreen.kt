package com.umamit.feature.collection.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.backup.recipe.RecipeShareText
import com.umamit.core.backup.recipe.ResourceRecipeShareLabels
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.alertCovers
import com.umamit.core.ui.rememberAlertGate
import com.umamit.core.ui.rememberExpandedSheetState
import com.umamit.core.ui.rememberPhotoPainter
import com.umamit.core.ui.teaTypePlaceholderColor
import com.umamit.feature.collection.R
import com.umamit.feature.collection.editor.sorteNameRes
import com.umamit.feature.collection.label

/** Test tags of SCR-32. */
object ReceiveRecipeTags {
    const val SCREEN = "receive_recipe"
    const val TAKE_OVER = "receive_recipe_take_over"
    const val OTHER_TEA = "receive_recipe_other_tea"
    const val NEW_TEA = "receive_recipe_new_tea"
    const val SHOP_LINK = "receive_recipe_shop_link"
    const val CLOSE_REFUSED = "receive_recipe_close_refused"
    const val SHEET = "receive_recipe_sheet"

    fun tea(id: String) = "receive_recipe_tea_$id"

    fun sheetTea(id: String) = "receive_recipe_sheet_tea_$id"
}

/** What SCR-32's controls do; [linkMessage] is CAT-17's text of a failed *Tee über Shop-Link laden* (03 §5). */
class ReceiveRecipeActions(
    val close: () -> Unit = {},
    val choose: (TeaChoice) -> Unit = {},
    val pick: (teaId: String) -> Unit = {},
    val loadShopLink: () -> Unit = {},
    val takeOver: () -> Unit = {},
    val linkMessage: (TeaLinkOutcome) -> Int = { R.string.receive_recipe_failed },
    val savePackage: (CatalogPurchase) -> Unit = {},
    val dismissPackageSheet: () -> Unit = {},
    val closePackageSheet: () -> Unit = {},
)

/**
 * SCR-32 „Rezept übernehmen" (03 SCR-32, PRF-08): a full-screen dialog like SCR-12 (✕ · title). A valid file shows its
 * summary, *Für welchen Tee?* as a radio group (the match, a tea picked with *Anderen Tee wählen …*, *Neuen Tee
 * anlegen*), *Tee über Shop-Link laden* when offered, and the sticky *Rezept übernehmen*; a refused file a centred
 * message with *Schließen*, nothing else. ✕ discards without asking: nothing was written. *Neuen Tee anlegen* „aus
 * dem Katalog" shows the catalog's package sheet through [packageSheet] (from the app: features don't depend on each
 * other). Both sheets give way to an alert other than [alertKey] (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveRecipeScreen(
    state: ReceiveRecipeUiState,
    actions: ReceiveRecipeActions,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
    photo: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
    packageSheet: @Composable (PackageSheetRequest) -> Unit = {},
) {
    val sheet = rememberAlertGate(alertKey)
    val ready = state as? ReceiveRecipeUiState.Ready
    Scaffold(
        modifier = modifier.testTag(ReceiveRecipeTags.SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.receive_recipe_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.close) {
                        Icon(painterResource(UmamiIcons.Close), stringResource(R.string.receive_recipe_close))
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PillButton(
                        text = stringResource(R.string.receive_recipe_take_over),
                        onClick = actions.takeOver,
                        enabled = ready.canTakeOver,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)
                            .testTag(ReceiveRecipeTags.TAKE_OVER),
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ReceiveRecipeUiState.Loading -> Unit
                is ReceiveRecipeUiState.Refused -> Refused(state, actions.close)
                is ReceiveRecipeUiState.Ready -> Ready(state, actions, onOtherTea = sheet::open, photo = photo)
            }
        }
    }
    if (ready != null) {
        AlertGated(sheet) {
            TeaSheet(
                teas = ready.teas,
                photo = photo,
                onPick = { id ->
                    sheet.close()
                    actions.pick(id)
                },
                onDismiss = sheet::close,
            )
        }
        ready.packageSheet?.let { PackageSheetSlot(it, actions, alertKey, packageSheet) }
    }
}

/** The package sheet while no alert plays that it was not opened in; such an alert removes it at once (rule 8). */
@Composable
private fun PackageSheetSlot(
    sheet: PackageSheet,
    actions: ReceiveRecipeActions,
    alertKey: String?,
    content: @Composable (PackageSheetRequest) -> Unit,
) {
    val openedIn = remember(sheet.catalogKey) { alertKey }
    val covered = alertCovers(alertKey, openedIn)
    val close by rememberUpdatedState(actions.closePackageSheet)
    LaunchedEffect(covered) { if (covered) close() }
    if (!covered) {
        content(
            PackageSheetRequest(
                catalogKey = sheet.catalogKey,
                saving = sheet.saving,
                failed = sheet.failed,
                onSave = actions.savePackage,
                onDismiss = actions.dismissPackageSheet,
            ),
        )
    }
}

@Composable
private fun Refused(state: ReceiveRecipeUiState.Refused, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = UmamiSpacing.SideMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(state.refusal.messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.padding(top = UmamiSpacing.Medium).testTag(ReceiveRecipeTags.CLOSE_REFUSED),
        ) {
            Text(stringResource(R.string.receive_recipe_close))
        }
    }
}

@Composable
private fun Ready(
    state: ReceiveRecipeUiState.Ready,
    actions: ReceiveRecipeActions,
    onOtherTea: () -> Unit,
    photo: @Composable (path: String?) -> Painter?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = UmamiSpacing.Large)) {
        Summary(state.recipe)
        SectionHeader(
            title = stringResource(R.string.receive_recipe_which_tea),
            modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Large).semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            val match = state.match
            if (match == null) {
                Text(
                    text = stringResource(R.string.receive_recipe_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
                )
            } else {
                val how = stringResource(
                    if (state.matchKind == MatchKind.CATALOG) {
                        R.string.receive_recipe_match_catalog
                    } else {
                        R.string.receive_recipe_match_name
                    },
                )
                TeaChoiceRow(match, how, state.choice == TeaChoice.Existing(match.id), photo) {
                    actions.choose(TeaChoice.Existing(match.id))
                }
            }
            state.picked?.let { picked ->
                TeaChoiceRow(picked, null, state.choice == TeaChoice.Existing(picked.id), photo) {
                    actions.choose(TeaChoice.Existing(picked.id))
                }
            }
            if (state.offersNewTea) {
                NewTeaRow(
                    fromCatalog = state.catalogKey != null,
                    selected = state.choice == TeaChoice.NewTea,
                    onClick = { actions.choose(TeaChoice.NewTea) },
                )
            }
        }
        TextButton(
            onClick = onOtherTea,
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small).testTag(ReceiveRecipeTags.OTHER_TEA),
        ) {
            Text(stringResource(R.string.receive_recipe_other_tea))
        }
        if (state.offersShopLink) ShopLink(state, actions)
        val failure = state.failure
        if (failure != null) {
            Text(
                text = stringResource(
                    if (failure == TakeOverResult.LimitReached) {
                        R.string.receive_recipe_full
                    } else {
                        R.string.receive_recipe_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
            )
        }
    }
}

/** The recipe's summary card; one block for TalkBack. */
@Composable
private fun Summary(recipe: SharedRecipe) {
    val resources = LocalResources.current
    val labels = ResourceRecipeShareLabels(resources)
    // The share text's lines: the title, the summary („Kyusu · 4,0 g auf 120 ml · 60 °C · Gefäß: Kyusu"), the steps.
    val lines = RecipeShareText.build(recipe, labels).lines()
    val tea = recipe.tea
    val kind = listOfNotNull(
        stringResource(tea.category.label),
        tea.styleKey?.let(::sorteNameRes)?.let { stringResource(it) },
    ).joinToString(stringResource(R.string.collection_tea_recipe_separator))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(UmamiSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
        ) {
            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(lines.getOrElse(1) { "" }, style = MaterialTheme.typography.bodyMedium)
            lines.drop(2).take(recipe.steps.size).forEach { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.receive_recipe_for_tea, tea.name, kind),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = UmamiSpacing.XSmall),
            )
        }
    }
}

@Composable
private fun TeaChoiceRow(
    tea: TeaOption,
    how: String?,
    selected: Boolean,
    photo: @Composable (path: String?) -> Painter?,
    onClick: () -> Unit,
) {
    val meta = when {
        tea.full -> stringResource(R.string.receive_recipe_full)
        how != null -> how
        else -> tea.sorte ?: stringResource(tea.category.label)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = !tea.full, role = Role.RadioButton, onClick = onClick)
            .padding(start = UmamiSpacing.Small)
            .testTag(ReceiveRecipeTags.tea(tea.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = !tea.full)
        TeaListItem(
            title = tea.name,
            meta = meta,
            subtitle = tea.secondaryName,
            thumbnail = photo(tea.photoPath),
            placeholderColor = teaTypePlaceholderColor(tea.sorte),
            showDivider = false,
            modifier = Modifier.weight(1f).alpha(if (tea.full) DISABLED_ALPHA else 1f),
        )
    }
}

@Composable
private fun NewTeaRow(fromCatalog: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall)
            .testTag(ReceiveRecipeTags.NEW_TEA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = UmamiSpacing.XSmall)) {
            Text(stringResource(R.string.receive_recipe_new_tea), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (fromCatalog) {
                        R.string.receive_recipe_new_from_catalog
                    } else {
                        R.string.receive_recipe_new_without_catalog
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShopLink(state: ReceiveRecipeUiState.Ready, actions: ReceiveRecipeActions) {
    Column(Modifier.padding(horizontal = UmamiSpacing.Small)) {
        TextButton(
            onClick = actions.loadShopLink,
            enabled = !state.linkLoading,
            modifier = Modifier.testTag(ReceiveRecipeTags.SHOP_LINK),
        ) {
            if (state.linkLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = UmamiSpacing.XSmall).size(PROGRESS_SIZE),
                    strokeWidth = 2.dp,
                )
                Text(stringResource(R.string.receive_recipe_shop_link_loading))
            } else {
                Text(stringResource(R.string.receive_recipe_shop_link))
            }
        }
        Text(
            text = stringResource(R.string.receive_recipe_shop_link_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small),
        )
        state.linkFailure?.let { outcome ->
            Text(
                text = stringResource(actions.linkMessage(outcome)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
            )
        }
    }
}

/** *Anderen Tee wählen …*: the collection, archived teas included; a tea with 10 recipes greyed out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeaSheet(
    teas: List<TeaOption>,
    photo: @Composable (path: String?) -> Painter?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberExpandedSheetState(UmamiTheme.reduceMotion),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag(ReceiveRecipeTags.SHEET),
    ) {
        Text(
            text = stringResource(R.string.receive_recipe_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
                .semantics { heading() },
        )
        LazyColumn {
            items(teas, key = { it.id }) { tea ->
                val archived = stringResource(R.string.receive_recipe_archived)
                val meta = when {
                    tea.full -> stringResource(R.string.receive_recipe_full)

                    else -> listOfNotNull(
                        tea.sorte ?: stringResource(tea.category.label),
                        archived.takeIf { tea.isArchived },
                    ).joinToString(stringResource(R.string.collection_tea_recipe_separator))
                }
                TeaListItem(
                    title = tea.name,
                    meta = meta,
                    subtitle = tea.secondaryName,
                    thumbnail = photo(tea.photoPath),
                    placeholderColor = teaTypePlaceholderColor(tea.sorte),
                    onClick = if (tea.full) null else ({ onPick(tea.id) }),
                    modifier = Modifier
                        .testTag(ReceiveRecipeTags.sheetTea(tea.id))
                        .alpha(if (tea.full) DISABLED_ALPHA else 1f),
                )
            }
        }
    }
}

/** Material's disabled content alpha, for a tea with 10 recipes. */
private const val DISABLED_ALPHA = 0.38f

private val PROGRESS_SIZE = 18.dp
