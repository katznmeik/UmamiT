package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.collection.detail.ArchivedTea
import com.umamit.feature.collection.detail.ArchivedTeaSnackbar
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.editor.SortePickerScreen
import com.umamit.feature.collection.editor.TeaEditorRoute
import com.umamit.feature.collection.recipe.RecipeEditorRoute

/**
 * What Sammlung's *Meine Tees* segment gets from the shell (SCR-08).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property openTea opens a collection tea's detail (SCR-09) by its id, in the Sammlung tab's stack.
 * @property alertKey the alert playing now, or null: the FAB's choice and the chips' menus give way to
 *   another (rule 8).
 * @property addFromCatalog *Aus dem Katalog* and *Im Katalog stöbern*: the *Katalog* segment (SCR-19).
 * @property addWithoutCatalog *Ohne Katalog*: *Sorte wählen* (SCR-11).
 * @property snackbarShowing the shell's snackbar „… archiviert" shows at the bottom: the FAB rises above it (F13).
 */
internal class MyTeasTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val openTea: (teaId: String) -> Unit,
    val alertKey: String? = null,
    val addFromCatalog: () -> Unit = {},
    val addWithoutCatalog: () -> Unit = {},
    val snackbarShowing: Boolean = false,
)

/**
 * The collection screens of the Sammlung stack: the *Meine Tees* list ([myTeas]; the app passes [MyTeasTabContent],
 * the default is „Noch keine Tees" without data), a tea's detail (SCR-09), *Sorte wählen* (SCR-11), the tea editor
 * (SCR-10), the recipe editor (SCR-12) and the snackbar „*Tanabata* archiviert" · *Rückgängig* that the shell shows
 * over the screen below the closed detail, wherever that is (D-100). The app uses the real routes; tests replace them.
 */
internal class CollectionStackScreens(
    val myTeas: @Composable (MyTeasTab) -> Unit = { PlaceholderMyTeas(it) },
    val tea: @Composable (TeaDetailHost) -> Unit = { TeaDetailRoute(it) },
    val sortePicker: @Composable (onBack: () -> Unit, onPick: (styleKey: String) -> Unit) -> Unit =
        { onBack, onPick -> SortePickerScreen(onBack = onBack, onPick = onPick) },
    val teaEditor: @Composable (
        TeaEditorHost,
    ) -> Unit = { TeaEditorRoute(onClose = it.onClose, onSave = it.onSave, alertKey = it.alertKey) },
    val recipeEditor: @Composable (RecipeEditorHost) -> Unit =
        {
            RecipeEditorRoute(
                onClose = it.onClose,
                onSave = { _ -> it.onDone() },
                onDelete = it.onDone,
                alertKey = it.alertKey,
            )
        },
    // Only composed with a notice: its view model exists only while there is something to undo.
    val archivedSnackbar: @Composable (archived: ArchivedTea?, onDone: () -> Unit, modifier: Modifier) -> Unit =
        { archived, onDone, modifier -> if (archived != null) ArchivedTeaSnackbar(archived, onDone, modifier) },
)

/**
 * What the tea editor (SCR-10) gets from the Sammlung stack: [onClose] for ✕ and back, [onSave] after *Speichern*
 * (after an add, [isNew], the new tea's detail replaces the editor), and [alertKey], the alert playing now: its
 * menus and dialogs give way to another (rule 8).
 */
internal class TeaEditorHost(
    val onClose: () -> Unit,
    val onSave: (teaId: String, isNew: Boolean) -> Unit,
    val alertKey: String? = null,
)

/**
 * What the recipe editor (SCR-12) gets from the Sammlung stack: [onClose] for ✕ and back, [onDone] after *Speichern*
 * or *Löschen*; either returns to the tea's detail. Its menus and dialogs give way to another alert than
 * [alertKey] (rule 8).
 */
internal class RecipeEditorHost(val onClose: () -> Unit, val onDone: () -> Unit, val alertKey: String? = null)

/**
 * What the Sammlung tab's collection screens get from the shell: the [screens], [onArchivedChange] for the tea
 * archived on its detail (the shell shows its snackbar), and [openSetup], the setup of a tea
 * with a recipe (null: its default) above the shell.
 */
internal class CollectionWiring(
    val screens: CollectionStackScreens,
    val onArchivedChange: (ArchivedTea?) -> Unit,
    val openSetup: (teaId: String, recipeId: String?) -> Unit,
)
