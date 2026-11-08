package com.umamit.feature.collection.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.StockSheet
import com.umamit.core.ui.rememberAlertGate
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
 * save; the browser for INV-09's link; and *+ Foto*, the photo picker (TEA-05).
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
    val addPhoto: () -> Unit = {},
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
    photo: @Composable (path: String) -> Painter? = { rememberPhotoPainter(it) },
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
    photo: @Composable (path: String) -> Painter?,
    modifier: Modifier = Modifier,
) {
    val deleteAsk = rememberAlertGate(alertKey)
    val stockSheet = rememberAlertGate(alertKey)
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
                    PhotoPager(
                        tea = tea,
                        placeholderColor = UmamiTheme.extendedColors.teaTypes.forStyle(tea.styleKey),
                        photo = photo,
                        onAddPhoto = actions.addPhoto,
                    )
                    Header(tea)
                    Stats(tea)
                    Recipes(tea, actions)
                    Stock(tea, onCorrect = stockSheet::open, onReorder = actions.openShop)
                    FreshnessSection(tea.freshness)
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
    AlertGated(deleteAsk) {
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
    AlertGated(stockSheet) {
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
    val menu = rememberAlertGate(alertKey)
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
            AlertGatedDropdownMenu(menu) { TeaMenu(tea, actions, onDelete, close = menu::close) }
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

private val FAB_CLEARANCE = 88.dp
