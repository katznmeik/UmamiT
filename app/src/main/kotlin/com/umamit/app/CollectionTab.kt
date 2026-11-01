package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.collection.detail.ArchivedTea
import com.umamit.feature.collection.detail.ArchivedTeaSnackbar
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute
import com.umamit.feature.collection.editor.SortePickerScreen
import com.umamit.feature.collection.editor.TeaEditorRoute

/**
 * What Sammlung's *Meine Tees* segment gets from the shell (SCR-08).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property openTea opens a collection tea's detail (SCR-09) by its id, in the Sammlung tab's stack.
 * @property alerting whether an alert plays now: the FAB's choice and the chips' menus give way (rule 8).
 * @property addFromCatalog *Aus dem Katalog* and *Im Katalog stöbern*: the *Katalog* segment (SCR-19).
 * @property addWithoutCatalog *Ohne Katalog*: *Sorte wählen* (SCR-11).
 */
internal class MyTeasTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val openTea: (teaId: String) -> Unit,
    val alerting: Boolean = false,
    val addFromCatalog: () -> Unit = {},
    val addWithoutCatalog: () -> Unit = {},
)

/**
 * The collection screens of the Sammlung stack: the *Meine Tees* list ([myTeas]; the app passes [MyTeasTabContent],
 * the default is „Noch keine Tees" without data), a tea's detail (SCR-09), *Sorte wählen* (SCR-11), the tea editor
 * (SCR-10) and the snackbar „*Tanabata* archiviert" · *Rückgängig* that the list shows after *Archivieren* on the
 * detail (D-100). The app uses the real routes; tests replace them.
 */
internal class CollectionStackScreens(
    val myTeas: @Composable (MyTeasTab) -> Unit = { PlaceholderMyTeas(it) },
    val tea: @Composable (TeaDetailHost) -> Unit = { TeaDetailRoute(it) },
    val sortePicker: @Composable (onBack: () -> Unit, onPick: (styleKey: String) -> Unit) -> Unit =
        { onBack, onPick -> SortePickerScreen(onBack = onBack, onPick = onPick) },
    val teaEditor: @Composable (TeaEditorHost) -> Unit = { TeaEditorRoute(onClose = it.onClose, onSave = it.onSave) },
    // Only composed with a notice: its view model exists only while there is something to undo.
    val archivedSnackbar: @Composable (archived: ArchivedTea?, onDone: () -> Unit, modifier: Modifier) -> Unit =
        { archived, onDone, modifier -> if (archived != null) ArchivedTeaSnackbar(archived, onDone, modifier) },
)

/**
 * What the tea editor (SCR-10) gets from the Sammlung stack: [onClose] for ✕ and back, [onSave] after *Speichern*
 * (after an add, [isNew], the new tea's detail replaces the editor).
 */
internal class TeaEditorHost(val onClose: () -> Unit, val onSave: (teaId: String, isNew: Boolean) -> Unit)

/**
 * What the Sammlung tab's collection screens get from the shell: the [screens], the tea [archived] on its detail
 * and not yet answered on the list ([onArchivedChange] sets or clears it), and [openSetup], the setup of a tea
 * with a recipe (null: its default) above the shell.
 */
internal class CollectionWiring(
    val screens: CollectionStackScreens,
    val archived: ArchivedTea?,
    val onArchivedChange: (ArchivedTea?) -> Unit,
    val openSetup: (teaId: String, recipeId: String?) -> Unit,
)
