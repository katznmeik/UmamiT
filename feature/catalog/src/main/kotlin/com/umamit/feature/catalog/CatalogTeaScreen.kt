package com.umamit.feature.catalog

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.ui.rememberPhotoPainter

/** The catalog tea detail's scrolling content (SCR-21). */
const val CATALOG_TEA_TAG = "catalog_tea"

/** *Im Shop öffnen* (CAT-11). */
const val CATALOG_TEA_OPEN_SHOP_TAG = "catalog_tea_open_shop"

/** *In Sammlung aufnehmen* / *Nachkauf erfassen* (CAT-12). */
const val CATALOG_TEA_ADD_TAG = "catalog_tea_add"

/**
 * A catalog tea's detail (SCR-21, CAT-11) with its view model and its add sheet (SCR-23, CAT-12).
 * *Im Shop öffnen* hands the shop page to the browser: the user's own navigation, the app sends
 * no request (GEN-01, D-072). [onPurchaseSave] gets the collection tea of each saved add or
 * repurchase.
 *
 * **Alerts first (rule 8):** while an alert plays ([alerting]) the add sheet is not composed, so
 * it leaves in that frame, and it is closed for good; it cannot be opened during an alert.
 */
@Composable
fun CatalogTeaRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    alerting: Boolean = false,
    onPurchaseSave: (String) -> Unit = {},
    viewModel: CatalogTeaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnPurchaseSave by rememberUpdatedState(onPurchaseSave)
    LaunchedEffect(alerting) { if (alerting) viewModel.closeSheet() }
    LaunchedEffect(viewModel) { viewModel.added.collect { currentOnPurchaseSave(it) } }
    CatalogTeaScreen(
        state = state,
        onBack = onBack,
        onOpenShop = context::openInBrowser,
        onAdd = { if (!alerting) viewModel.openSheet() },
        modifier = modifier,
    )
    val open = sheet
    if (open != null && !alerting && state.tea != null) {
        CatalogAddSheet(
            state = open,
            actions = AddSheetActions(
                onSelectSize = viewModel::selectSize,
                onPriceChange = viewModel::editPrice,
                onGramsChange = viewModel::editGrams,
                onDateChange = viewModel::pickDate,
                onSave = viewModel::save,
            ),
            onDismissRequest = viewModel::dismissSheet,
        )
    }
}

/**
 * The stateless detail (SCR-21): the photo (or the type-coloured leaf, 03 §3.5), names, *Sorte*,
 * badges, price per 100 g, *In Sammlung aufnehmen* or, for a tea in the collection, *Nachkauf
 * erfassen* ([onAdd], CAT-12) and *Im Shop öffnen* (not for a tea no longer in the range, which
 * says „Nicht mehr im Sortiment", CAT-07 AC4); then the facts, the shop's taste scales, aroma
 * and taste, the description, the shop's brewing grid and text, verbatim, and the package sizes
 * with their prices. [photo] is the
 * tea's photo; tests pass a placeholder image.
 */
@Composable
fun CatalogTeaScreen(
    state: CatalogTeaUiState,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    photo: Painter? = rememberPhotoPainter(state.tea?.photoPath),
) {
    val tea = state.tea
    when {
        state.loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        tea == null -> ScreenScaffold(title = "", modifier = modifier, navigationIcon = { BackAction(onBack) }) {
            EmptyState(
                title = stringResource(R.string.catalog_tea_gone_title),
                body = stringResource(R.string.catalog_tea_gone_body),
                illustration = catalogEmptyIllustration(),
                illustrationTint = null,
            )
        }

        else -> Detail(
            tea = tea,
            shopUrl = state.shopUrl ?: tea.productUrl,
            owned = state.ownedTeaId != null,
            photo = photo,
            actions = DetailActions(onBack, onOpenShop, onAdd),
            modifier = modifier,
        )
    }
}

/** The detail's actions: back, *Im Shop öffnen*, *In Sammlung aufnehmen* / *Nachkauf erfassen*. */
private class DetailActions(val onBack: () -> Unit, val onOpenShop: (String) -> Unit, val onAdd: () -> Unit)

@Composable
private fun Detail(
    tea: CatalogTeaDetail,
    shopUrl: String,
    owned: Boolean,
    photo: Painter?,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .testTag(CATALOG_TEA_TAG),
        ) {
            Hero(photo, placeholderColor(tea.teaType))
            Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
                Header(tea)
                Spacer(Modifier.height(UmamiSpacing.Medium))
                PillButton(
                    text = stringResource(if (owned) R.string.catalog_tea_repurchase else R.string.catalog_tea_add),
                    onClick = actions.onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CATALOG_TEA_ADD_TAG),
                )
                // A tea no longer in the range has no shop page to open (CAT-07 AC4, as INV-09 AC2).
                if (!tea.discontinued) {
                    Spacer(Modifier.height(UmamiSpacing.XSmall))
                    OutlinedButton(
                        onClick = { actions.onOpenShop(shopUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CATALOG_TEA_OPEN_SHOP_TAG),
                    ) {
                        Text(stringResource(R.string.catalog_tea_open_shop))
                    }
                }
            }
            Facts(tea)
            Taste(tea)
            Description(tea)
            Brewing(tea)
            Recipes(tea)
            Packages(tea)
            Spacer(Modifier.height(UmamiSpacing.Large))
        }
        // Over the photo, it stays put while the page scrolls; its own surface keeps it legible on any photo.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = BACK_SURFACE_ALPHA),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                .padding(UmamiSpacing.XSmall),
        ) {
            BackAction(actions.onBack)
        }
    }
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

@Composable
private fun Header(tea: CatalogTeaDetail) = Column {
    Spacer(Modifier.height(UmamiSpacing.Large))
    Text(text = tea.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    tea.secondaryName?.let {
        Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val tags = listOfNotNull(
        stringResource(R.string.catalog_tea_discontinued).takeIf { tea.discontinued },
        tea.teaType,
        stringResource(R.string.catalog_tea_organic).takeIf { tea.isOrganic },
        stringResource(R.string.catalog_tea_flavored).takeIf { tea.isFlavored },
        stringResource(R.string.catalog_tea_cold_brew).takeIf { tea.coldBrewSuitable },
        stringResource(R.string.catalog_tea_out_of_stock).takeIf { tea.available == false && !tea.discontinued },
    )
    if (tags.isNotEmpty()) {
        Spacer(Modifier.height(UmamiSpacing.Small))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            tags.forEach { Tag(it) }
        }
    }
    tea.basePriceCentsPer100g?.let {
        Spacer(Modifier.height(UmamiSpacing.Small))
        Text(
            text = stringResource(R.string.catalog_price_per_100g, CatalogFormat.euros(it)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XXSmall),
        )
    }
}

/** Opens [url] in the browser; without one, says so. */
internal fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.catalog_tea_no_browser, Toast.LENGTH_SHORT).show()
    }
}

private const val HERO_ASPECT_RATIO = 4f / 3f
private const val PLACEHOLDER_ALPHA = 0.22f
private const val BACK_SURFACE_ALPHA = 0.92f
private val HERO_LEAF_SIZE = 64.dp
