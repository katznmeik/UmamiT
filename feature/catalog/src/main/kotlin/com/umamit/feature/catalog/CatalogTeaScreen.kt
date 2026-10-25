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
import androidx.compose.runtime.getValue
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
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.ui.rememberPhotoPainter

/** The catalog tea detail's scrolling content (SCR-21). */
const val CATALOG_TEA_TAG = "catalog_tea"

/** *Im Shop öffnen* (CAT-11). */
const val CATALOG_TEA_OPEN_SHOP_TAG = "catalog_tea_open_shop"

/**
 * A catalog tea's detail (SCR-21, CAT-11) with its view model. *Im Shop öffnen* hands the shop
 * page to the browser: the user's own navigation, the app sends no request (GEN-01, D-072).
 */
@Composable
fun CatalogTeaRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogTeaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CatalogTeaScreen(state = state, onBack = onBack, onOpenShop = context::openInBrowser, modifier = modifier)
}

/**
 * The stateless detail (SCR-21): the photo (or the type-coloured leaf, 03 §3.5), names, *Sorte*,
 * badges, price per 100 g and *Im Shop öffnen*; then the facts, the shop's taste scales, aroma
 * and taste, the description and the shop's brewing grid and text, verbatim. [photo] is the
 * tea's photo; tests pass a placeholder image.
 */
@Composable
fun CatalogTeaScreen(
    state: CatalogTeaUiState,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
    modifier: Modifier = Modifier,
    photo: Painter? = rememberPhotoPainter(state.tea?.photoPath),
) {
    val tea = state.tea
    when {
        state.loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        tea == null -> ScreenScaffold(title = "", modifier = modifier, navigationIcon = { BackAction(onBack) }) {
            EmptyState(
                title = stringResource(R.string.catalog_tea_gone_title),
                body = stringResource(R.string.catalog_tea_gone_body),
            )
        }

        else -> Detail(tea, state.shopUrl ?: tea.productUrl, photo, onBack, onOpenShop, modifier)
    }
}

@Composable
private fun Detail(
    tea: CatalogTeaDetail,
    shopUrl: String,
    photo: Painter?,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
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
                OutlinedButton(
                    onClick = { onOpenShop(shopUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CATALOG_TEA_OPEN_SHOP_TAG),
                ) {
                    Text(stringResource(R.string.catalog_tea_open_shop))
                }
            }
            Facts(tea)
            Taste(tea)
            Description(tea)
            Brewing(tea)
            Recipes(tea)
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
            BackAction(onBack)
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
        tea.teaType,
        stringResource(R.string.catalog_tea_organic).takeIf { tea.isOrganic },
        stringResource(R.string.catalog_tea_flavored).takeIf { tea.isFlavored },
        stringResource(R.string.catalog_tea_cold_brew).takeIf { tea.coldBrewSuitable },
        stringResource(R.string.catalog_tea_out_of_stock).takeIf { tea.available == false },
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
