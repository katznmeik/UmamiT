package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.collection.detail.ArchivedTea
import com.umamit.feature.collection.detail.ArchivedTeaSnackbar
import com.umamit.feature.collection.detail.TeaDetailHost
import com.umamit.feature.collection.detail.TeaDetailRoute

/**
 * What Sammlung's *Meine Tees* segment gets from the shell (SCR-08).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property openTea opens a collection tea's detail (SCR-09) by its id, in the Sammlung tab's stack.
 */
internal class MyTeasTab(val modifier: Modifier, val scrollToTopRequest: Int, val openTea: (teaId: String) -> Unit)

/**
 * The collection screens of the Sammlung stack: the *Meine Tees* list ([myTeas]; null keeps the M1 placeholder),
 * a tea's detail (SCR-09) and the snackbar „*Tanabata* archiviert" · *Rückgängig* that the list shows after
 * *Archivieren* on the detail (D-100). The app uses the real routes; tests replace them.
 */
internal class CollectionStackScreens(
    val myTeas: (@Composable (MyTeasTab) -> Unit)? = null,
    val tea: @Composable (TeaDetailHost) -> Unit = { TeaDetailRoute(it) },
    // Only composed with a notice: its view model exists only while there is something to undo.
    val archivedSnackbar: @Composable (archived: ArchivedTea?, onDone: () -> Unit, modifier: Modifier) -> Unit =
        { archived, onDone, modifier -> if (archived != null) ArchivedTeaSnackbar(archived, onDone, modifier) },
)

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
