package com.umamit.feature.collection.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.StockSheet
import com.umamit.core.ui.rememberPhotoPainter
import com.umamit.feature.collection.R

/** The detail's scrolling content (SCR-09). */
const val TEA_DETAIL_TAG = "tea_detail"

/** The sticky *Brühen*. */
const val TEA_DETAIL_BREW_TAG = "tea_detail_brew"

/** The pencil FAB: the tea editor (SCR-10). */
const val TEA_DETAIL_EDIT_TAG = "tea_detail_edit"

/**
 * What the tea detail does: back; *Brühen* with a recipe (null: the default); the editors (SCR-10, SCR-12); ⋮
 * *Favorit*, *Archivieren*, *Wiederherstellen*, *Löschen* (the dialog's confirmed delete); *Bestand korrigieren*'s
 * save; and the browser for INV-09's link.
 */
class TeaDetailActions(
    val back: () -> Unit = {},
    val brew: (recipeId: String?) -> Unit = {},
    val editTea: () -> Unit = {},
    val editRecipe: (recipeId: String) -> Unit = {},
    val addRecipe: () -> Unit = {},
    val toggleFavorite: () -> Unit = {},
    val archive: () -> Unit = {},
    val restore: () -> Unit = {},
    val delete: () -> Unit = {},
    val correctStock: (grams: Double) -> Unit = {},
    val openShop: (url: String) -> Unit = {},
)

/**
 * The stateless tea detail (SCR-09, TEA-13; v0.4.0 per D-100): the hero photo (or the style's leaf), names, the
 * *Sorte* chip, the umami meter and the `StatTriplet` of sessions, last brew and stock; REZEPTE, VORRAT and, only
 * with content, BESCHREIBUNG, NOTIZEN and DETAILS. *Brühen* stays at the bottom (greyed out for an archived tea);
 * the pencil FAB opens the tea editor. ⋮ offers *Favorit* (checked), *Archivieren* or, for an archived tea,
 * *Wiederherstellen*, and *Löschen*, which asks first (03 §5); both greyed out for the tea of the active brew
 * session („Erst die Sitzung beenden", E15).
 *
 * **Alerts first (rule 8):** the menu, the delete question and the stock sheet each remember the alert they were
 * opened in ([alertKey]); another alert removes them in its first frame and closes them for good.
 */
@Composable
fun TeaDetailScreen(
    state: TeaDetailUiState,
    actions: TeaDetailActions,
    modifier: Modifier = Modifier,
    alertKey: String? = null,
    photo: Painter? = rememberPhotoPainter((state as? TeaDetailUiState.Ready)?.tea?.photoPath),
) {
    when (state) {
        TeaDetailUiState.Loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        TeaDetailUiState.Gone -> ScreenScaffold(
            title = "",
            modifier = modifier,
            navigationIcon = { BackAction(actions.back) },
        ) {
            EmptyState(
                title = stringResource(R.string.collection_tea_gone_title),
                body = stringResource(R.string.collection_tea_gone_body),
            )
        }

        is TeaDetailUiState.Ready -> Detail(state.tea, actions, alertKey, photo, modifier)
    }
}

@Composable
private fun Detail(
    tea: TeaDetail,
    actions: TeaDetailActions,
    alertKey: String?,
    photo: Painter?,
    modifier: Modifier = Modifier,
) {
    val deleteAsk = rememberGate(alertKey)
    val stockSheet = rememberGate(alertKey)
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .testTag(TEA_DETAIL_TAG),
                ) {
                    Hero(photo, UmamiTheme.extendedColors.teaTypes.forStyle(tea.styleKey))
                    Header(tea)
                    Stats(tea)
                    Recipes(tea, actions)
                    Stock(tea, onCorrect = stockSheet::open, onReorder = actions.openShop)
                    TextSection(R.string.collection_tea_section_description, tea.description)
                    TextSection(R.string.collection_tea_section_notes, tea.notes)
                    Details(tea.details)
                    // Room for the FAB above the last line.
                    Spacer(Modifier.height(FAB_CLEARANCE))
                }
                FloatingActionButton(
                    onClick = actions.editTea,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                        .padding(UmamiSpacing.Medium)
                        .testTag(TEA_DETAIL_EDIT_TAG),
                ) {
                    Icon(
                        painterResource(UmamiIcons.Edit),
                        contentDescription = stringResource(R.string.collection_tea_edit),
                    )
                }
            }
            BrewBar(tea, onBrew = { actions.brew(null) })
        }
        TopActions(tea, actions, alertKey, onDelete = deleteAsk::open)
    }
    if (deleteAsk.shown) {
        DeleteDialog(
            tea = tea,
            onArchiveInstead = {
                deleteAsk.close()
                actions.archive()
            },
            onDelete = {
                deleteAsk.close()
                actions.delete()
            },
            onDismiss = deleteAsk::close,
        )
    }
    // „Wie viel ist noch da?" (INV-03): gone in the frame another alert starts, without saving (rule 8).
    if (stockSheet.shown) {
        StockSheet(
            currentGrams = tea.stockGrams.coerceAtLeast(0.0),
            sessionLeafGrams = tea.sessionLeafGrams,
            onSave = { grams ->
                actions.correctStock(grams)
                stockSheet.close()
            },
            onDismissRequest = stockSheet::close,
        )
    }
}

/** The sticky *Brühen*: the setup with the default recipe; greyed out with „Tee ist archiviert" (D-100). */
@Composable
private fun BrewBar(tea: TeaDetail, onBrew: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            HorizontalDivider(thickness = UmamiSpacing.Hairline, color = MaterialTheme.colorScheme.outline)
            PillButton(
                text = stringResource(R.string.collection_tea_brew),
                onClick = onBrew,
                enabled = tea.canBrew,
                icon = painterResource(UmamiIcons.Play),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)
                    .testTag(TEA_DETAIL_BREW_TAG),
            )
            if (!tea.canBrew) {
                Text(
                    text = stringResource(R.string.collection_tea_brew_archived),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = UmamiSpacing.Small),
                )
            }
        }
    }
}

/** Back and ⋮ over the photo; each on its own surface, legible on any photo, staying put while the page scrolls. */
@Composable
private fun TopActions(tea: TeaDetail, actions: TeaDetailActions, alertKey: String?, onDelete: () -> Unit) {
    val menu = rememberGate(alertKey)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(UmamiSpacing.XSmall),
    ) {
        OverPhoto(Modifier.align(Alignment.TopStart)) { BackAction(actions.back) }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            OverPhoto {
                IconButton(onClick = menu::open) {
                    Icon(
                        painterResource(UmamiIcons.MoreVert),
                        contentDescription = stringResource(R.string.collection_tea_more),
                    )
                }
            }
            // Not composed while another alert plays: Material's menu would keep its popup for the exit animation
            // over the alert's first frames. A normal close still animates.
            if (alertKey == null || menu.shown) {
                DropdownMenu(expanded = menu.shown, onDismissRequest = menu::close) {
                    TeaMenu(tea, actions, onDelete, close = menu::close)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TeaMenu(tea: TeaDetail, actions: TeaDetailActions, onDelete: () -> Unit, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.collection_tea_favorite)) },
        onClick = {
            close()
            actions.toggleFavorite()
        },
        trailingIcon = if (tea.isFavorite) {
            { Icon(painterResource(UmamiIcons.Check), contentDescription = null) }
        } else {
            null
        },
    )
    if (tea.isArchived) {
        MenuItem(R.string.collection_tea_restore, blocked = false) {
            close()
            actions.restore()
        }
    } else {
        MenuItem(R.string.collection_tea_archive, blocked = tea.inActiveBrew) {
            close()
            actions.archive()
        }
    }
    MenuItem(R.string.collection_tea_delete, blocked = tea.inActiveBrew) {
        close()
        onDelete()
    }
}

/** A ⋮ item; [blocked] greys it out with „Erst die Sitzung beenden" (E15). */
@Composable
private fun MenuItem(label: Int, blocked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(label))
                if (blocked) {
                    Text(
                        text = stringResource(R.string.collection_tea_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
        enabled = !blocked,
    )
}

@Composable
private fun OverPhoto(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = OVER_PHOTO_ALPHA),
        // Explicit: the translucent colour matches no scheme colour, so contentColorFor would not find its ink.
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        content = content,
    )
}

/** 03 §5: „*Gyokuro* löschen? Deine 12 Journal-Einträge bleiben erhalten." · *Stattdessen archivieren* / *Löschen*. */
@Composable
private fun DeleteDialog(tea: TeaDetail, onArchiveInstead: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_tea_delete_title, tea.name)) },
        text = if (tea.sessionCount > 0) {
            {
                Text(
                    pluralStringResource(R.plurals.collection_tea_delete_body, tea.sessionCount, tea.sessionCount),
                )
            }
        } else {
            null
        },
        // An archived tea is archived already: only *Löschen*.
        dismissButton = if (tea.isArchived) {
            null
        } else {
            {
                TextButton(onClick = onArchiveInstead) {
                    Text(stringResource(R.string.collection_tea_delete_archive_instead))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.collection_tea_delete_confirm)) }
        },
    )
}

@Composable
private fun Hero(photo: Painter?, placeholderColor: Color) {
    val heroModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(HERO_ASPECT_RATIO)
    if (photo != null) {
        Image(painter = photo, contentDescription = null, modifier = heroModifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier = heroModifier.background(placeholderColor.copy(alpha = PLACEHOLDER_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UmamiIcons.Leaf),
                contentDescription = null,
                tint = placeholderColor,
                modifier = Modifier.size(HERO_LEAF_SIZE),
            )
        }
    }
}

/**
 * Something opened on the detail (the menu, the delete question, the stock sheet) that gives way to an alert:
 * it remembers the alert it was opened in; another alert hides it in that frame and closes it for good (rule 8,
 * as the setup's `AlertGate`).
 */
private class Gate(
    private val openState: MutableState<Boolean>,
    private val openedIn: MutableState<String?>,
    private val alertKey: String?,
) {
    val covered: Boolean get() = alertKey != null && alertKey != openedIn.value
    val shown: Boolean get() = openState.value && !covered

    fun open() {
        openedIn.value = alertKey
        openState.value = true
    }

    fun close() {
        openState.value = false
    }
}

@Composable
private fun rememberGate(alertKey: String?): Gate {
    val open = rememberSaveable { mutableStateOf(false) }
    val openedIn = rememberSaveable { mutableStateOf<String?>(null) }
    val gate = Gate(open, openedIn, alertKey)
    val covered by rememberUpdatedState(gate.covered)
    LaunchedEffect(covered) { if (covered) open.value = false }
    return gate
}

private const val HERO_ASPECT_RATIO = 4f / 3f
private const val PLACEHOLDER_ALPHA = 0.22f
private const val OVER_PHOTO_ALPHA = 0.92f
private val HERO_LEAF_SIZE = 64.dp
private val FAB_CLEARANCE = 88.dp
