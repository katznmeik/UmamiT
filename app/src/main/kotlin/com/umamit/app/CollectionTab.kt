package com.umamit.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.collection.detail.ArchivedTea
import com.umamit.feature.collection.detail.ArchivedTeaSnackbar
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.editor.SortePickerScreen
import com.umamit.feature.collection.editor.TeaEditorRoute
import com.umamit.feature.collection.receive.ReceiveRecipeHost
import com.umamit.feature.collection.receive.ReceiveRecipeRoute
import com.umamit.feature.collection.receive.TakenOverRecipe
import com.umamit.feature.collection.receive.TakenOverSnackbar
import com.umamit.feature.collection.recipe.RecipeEditorRoute

/**
 * What Sammlung's *Meine Tees* segment gets from the shell (SCR-08).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property openTea opens a collection tea's detail (SCR-09) by its id, in the Sammlung tab's stack.
 * @property alertKey the alert playing now, or null: the chips' menus give way to another (rule 8).
 * @property addFromCatalog the empty state's *Im Katalog stöbern*: the *Katalog* segment (SCR-19). Adding a tea is
 *   Sammlung's ⋮ menu, wired with [CollectionScreen][com.umamit.feature.collection.CollectionScreen] (D-102).
 */
internal class MyTeasTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val openTea: (teaId: String) -> Unit,
    val alertKey: String? = null,
    val addFromCatalog: () -> Unit = {},
)

/**
 * The collection screens of the Sammlung stack: the *Meine Tees* list ([myTeas]; the app passes [MyTeasTabContent],
 * the default is „Noch keine Tees" without data), a tea's detail (SCR-09), *Sorte wählen* (SCR-11), the tea editor
 * (SCR-10), the recipe editor (SCR-12), *Rezept übernehmen* (SCR-32, PRF-08) with its „Rezept „…" übernommen" over
 * the detail it opens, and the snackbar „*Tanabata* archiviert" · *Rückgängig* that the shell shows over the screen
 * below the closed detail, wherever that is (D-100). The app uses the real routes; tests replace them.
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
    val receiveRecipe: @Composable (ReceiveRecipeHost) -> Unit = { ReceiveRecipeRoute(it) },
    // The snackbars show in the shell's one queue ([SnackbarHostState]), so two never overlap.
    val takenOverSnackbar: @Composable (notice: TakenOverRecipe?, onDone: () -> Unit, host: SnackbarHostState) -> Unit =
        { notice, onDone, host -> TakenOverSnackbar(notice, onDone, hostState = host) },
    // Only composed with a notice: its view model exists only while there is something to undo.
    val archivedSnackbar: @Composable (archived: ArchivedTea?, onDone: () -> Unit, host: SnackbarHostState) -> Unit =
        { archived, onDone, host -> if (archived != null) ArchivedTeaSnackbar(archived, onDone, hostState = host) },
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
 * with a recipe (null: its default) above the shell, and [showInJournal], the tea detail's *Alle anzeigen* (TEA-13):
 * the Journal tab at its root, filtered to the tea. SCR-32 reads its file from [recipeFiles] and opens for
 * [pendingRecipe] (a token, 0 for none); [importRecipe] is
 * *Meine Tees*' ⋮ *Rezept importieren* (the system file picker); [onTakenOver] gets the recipe SCR-32 added.
 */
internal class CollectionWiring(
    val screens: CollectionStackScreens,
    val onArchivedChange: (ArchivedTea?) -> Unit,
    val openSetup: (teaId: String, recipeId: String?) -> Unit,
    val showInJournal: (teaId: String) -> Unit = {},
    val recipeFiles: ReceivedRecipeFiles = ReceivedRecipeFiles(),
    val pendingRecipe: Long = 0L,
    val importRecipe: () -> Unit = {},
    val onTakenOver: (TakenOverRecipe) -> Unit = {},
)
