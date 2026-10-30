package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.feature.catalog.CatalogContent
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogUiState
import com.umamit.feature.catalog.GuideArticleRoute
import com.umamit.feature.catalog.GuideListRoute
import com.umamit.feature.catalog.TeaLinkShare
import com.umamit.feature.catalog.CatalogTeaRoute as CatalogTeaScreenRoute

/**
 * What Sammlung's *Katalog* segment gets from the shell (SCR-19).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property alerting whether an alert plays now: the refresh sheet gives way (rule 8).
 * @property openTea opens a catalog tea's detail (SCR-21) by its key, in the Sammlung tab's stack.
 * @property refreshRequest the ⋮ menu's *Katalog aktualisieren* counter (CAT-07, D-049).
 * @property openGuides opens the *Ratgeber* (CAT-09), in the Sammlung tab's stack.
 * @property pasteLinkRequest the ⋮ menu's *Link einfügen* counter (CAT-17).
 * @property sharedLink a link shared to the app, not taken yet (CAT-17); [onTakeSharedLink] takes it.
 * @property refreshSheetRequest the token of a tap on a refresh notification (SCR-22, D-097); 0 for none.
 */
internal data class CatalogTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val alerting: Boolean,
    val refreshRequest: Int = 0,
    val openTea: (String) -> Unit = {},
    val openGuides: () -> Unit = {},
    val pasteLinkRequest: Int = 0,
    val sharedLink: TeaLinkShare? = null,
    val onTakeSharedLink: (Long) -> Unit = {},
    val refreshSheetRequest: Long = 0L,
)

/**
 * The catalog segment with its view model and the refresh (#64, #65); a row opens the tea's
 * detail (#66), and so does a tea from a pasted or shared shop link (#98).
 */
@Composable
internal fun CatalogTabContent(tab: CatalogTab) {
    CatalogRoute(
        alerting = tab.alerting,
        onOpenTea = tab.openTea,
        onOpenGuides = tab.openGuides,
        modifier = tab.modifier,
        scrollToTopRequest = tab.scrollToTopRequest,
        refreshRequest = tab.refreshRequest,
        pasteLinkRequest = tab.pasteLinkRequest,
        sharedLink = tab.sharedLink,
        onTakeSharedLink = tab.onTakeSharedLink,
        refreshSheetRequest = tab.refreshSheetRequest,
    )
}

/** A catalog without data, for tests and previews of the shell: the empty state. */
@Composable
internal fun PlaceholderCatalog(tab: CatalogTab) {
    CatalogContent(state = CatalogUiState(loading = false), onLoad = {}, onShowProgress = {}, modifier = tab.modifier)
}

/**
 * What a catalog tea's detail (SCR-21) gets from the shell.
 *
 * @property onBack back to the catalog list.
 * @property alerting whether an alert plays now: the add sheet gives way (rule 8, D-085).
 * @property onPurchaseSave the collection tea of each saved add or repurchase (SCR-23): in the
 *   tea picker's flow it opens the setup of that tea (D-073, D-088).
 */
internal class CatalogTeaHost(
    val onBack: () -> Unit,
    val alerting: Boolean,
    val onPurchaseSave: (teaId: String) -> Unit,
)

/** A catalog tea's detail with its view model and the add sheet (#66, #71). */
@Composable
internal fun CatalogTeaContent(host: CatalogTeaHost) {
    CatalogTeaScreenRoute(onBack = host.onBack, alerting = host.alerting, onPurchaseSave = host.onPurchaseSave)
}

/**
 * The catalog screens in the Sammlung stack below the list: a tea's detail (SCR-21) and the
 * *Ratgeber* with its articles (SCR-20, CAT-09). The app uses the real routes; tests replace them.
 */
internal class CatalogStackScreens(
    val tea: @Composable (CatalogTeaHost) -> Unit = { CatalogTeaContent(it) },
    val guides: @Composable (onBack: () -> Unit, onOpenGuide: (String) -> Unit) -> Unit = { onBack, onOpenGuide ->
        GuideListRoute(onBack = onBack, onOpenGuide = onOpenGuide)
    },
    val guide: @Composable (
        onBack: () -> Unit,
        onOpenTea: (String) -> Unit,
        onOpenGuide: (String) -> Unit,
    ) -> Unit = { onBack, onOpenTea, onOpenGuide ->
        GuideArticleRoute(onBack = onBack, onOpenTea = onOpenTea, onOpenGuide = onOpenGuide)
    },
)
