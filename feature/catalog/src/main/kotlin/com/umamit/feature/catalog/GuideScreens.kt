package com.umamit.feature.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideLink
import com.umamit.core.ui.rememberPhotoPainter
import java.net.URI
import java.net.URISyntaxException
import java.time.ZoneId

/** The guide article's scrolling content (SCR-20). */
const val GUIDE_TAG = "guide"

/** *Im Browser öffnen* (SCR-20). */
const val GUIDE_OPEN_BROWSER_TAG = "guide_open_browser"

/** The *Ratgeber* list (CAT-09). */
const val GUIDE_LIST_TAG = "guide_list"

/** The *Ratgeber* list with its view model (CAT-09): a tap on a guide opens its article (SCR-20). */
@Composable
fun GuideListRoute(
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GuideListScreen(state = state, onBack = onBack, onOpenGuide = onOpenGuide, modifier = modifier)
}

/**
 * The stateless *Ratgeber* list (CAT-09): the header illustration A11 (the placeholder landscape
 * until the PO's image arrives, docs/design/assets.md), a line on what it is, then the guides by
 * title with their hero as thumbnail and their date. [image] loads a hero file; tests pass
 * placeholder images.
 */
@Composable
fun GuideListScreen(
    state: GuideListUiState,
    onBack: () -> Unit,
    onOpenGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    image: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
) {
    ScreenScaffold(
        title = stringResource(R.string.guides_title),
        modifier = modifier.testTag(GUIDE_LIST_TAG),
        navigationIcon = { BackAction(onBack) },
        verticalArrangement = Arrangement.Top,
    ) {
        if (state.loading) return@ScreenScaffold
        if (state.guides.isEmpty()) {
            // Its own illustration stands in for the header.
            EmptyState(
                title = stringResource(R.string.guides_empty_title),
                body = stringResource(R.string.guides_empty_body),
            )
            return@ScreenScaffold
        }
        // A11 comes from the PO (docs/design/assets.md); the placeholder landscape stands in.
        Image(
            painter = painterResource(UmamiIcons.LandscapePlaceholder),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HEADER_RATIO)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        )
        Text(
            text = stringResource(R.string.guides_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        )
        val source = stringResource(R.string.guide_source)
        state.guides.forEachIndexed { index, guide ->
            TeaListItem(
                title = guide.title,
                meta = guide.publishedAtMillis?.let { CatalogFormat.fullDate(it, zone) } ?: source,
                onClick = { onOpenGuide(guide.slug) },
                thumbnail = image(guide.heroPath),
                placeholderColor = UmamiTheme.extendedColors.teaTypes.sencha,
                showDivider = index < state.guides.lastIndex,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(UmamiSpacing.Large))
    }
}

/**
 * A guide's article (SCR-20, CAT-09) with its view model. A link to a catalog tea opens the tea
 * ([onOpenTea]), a link to a stored guide that guide ([onOpenGuide]), both inside the app; every
 * other link and *Im Browser öffnen* go to the browser (the user's own navigation; the app sends
 * no request, GEN-01).
 */
@Composable
fun GuideArticleRoute(
    onBack: () -> Unit,
    onOpenTea: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    GuideArticleScreen(
        state = state,
        onBack = onBack,
        onOpenTea = onOpenTea,
        onOpenGuide = onOpenGuide,
        onOpenWeb = context::openInBrowser,
        modifier = modifier,
    )
}

/**
 * The stateless article (SCR-20): the hero image (if any), the serif title, the source line
 * „Tee-Kontor Kiel · Tee-Info" with the date, the converted text and *Im Browser öffnen*.
 * [image] loads a local image file; tests pass placeholder images.
 */
@Composable
@Suppress("LongParameterList") // the state, the four ways out and the image loader
fun GuideArticleScreen(
    state: GuideUiState,
    onBack: () -> Unit,
    onOpenTea: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    image: @Composable (path: String?) -> Painter? = { rememberPhotoPainter(it) },
) {
    val guide = state.guide
    when {
        state.loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        guide == null -> ScreenScaffold(title = "", modifier = modifier, navigationIcon = { BackAction(onBack) }) {
            EmptyState(
                title = stringResource(R.string.guide_gone_title),
                body = stringResource(R.string.guide_gone_body),
            )
        }

        else -> Article(
            guide = guide,
            onBack = onBack,
            onLink = { target ->
                when (val link = GuideLink.parse(target)) {
                    is GuideLink.Tea -> onOpenTea(link.key)

                    is GuideLink.Guide -> if (link.slug in state.storedSlugs) {
                        onOpenGuide(link.slug)
                    } else {
                        // Gone from the catalog since: the shop's page instead (D-087).
                        shopGuidePage(guide.url, link.slug)?.let(onOpenWeb)
                    }

                    is GuideLink.Web -> onOpenWeb(link.url)

                    null -> Unit
                }
            },
            onOpenWeb = onOpenWeb,
            zone = zone,
            image = image,
            modifier = modifier,
        )
    }
}

@Composable
@Suppress("LongParameterList") // the guide, its ways out, the zone and the image loader
private fun Article(
    guide: GuideDetail,
    onBack: () -> Unit,
    onLink: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    zone: ZoneId,
    image: @Composable (path: String?) -> Painter?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .testTag(GUIDE_TAG),
        ) {
            val hero = image(guide.heroPath)
            if (hero != null) {
                Image(
                    painter = hero,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(HERO_RATIO),
                )
            } else {
                // Room for the back button.
                Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)))
                Spacer(Modifier.height(BACK_ROW_HEIGHT))
            }
            Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
                Spacer(Modifier.height(UmamiSpacing.Large))
                Text(
                    text = guide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(UmamiSpacing.XXSmall))
                Text(
                    text = guide.publishedAtMillis?.let {
                        stringResource(R.string.guide_source_dated, CatalogFormat.fullDate(it, zone))
                    } ?: stringResource(R.string.guide_source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(UmamiSpacing.Medium))
                GuideBody(document = guide.body, onLink = onLink, image = { image(it) })
                Spacer(Modifier.height(UmamiSpacing.Large))
                OutlinedButton(
                    onClick = { onOpenWeb(guide.url) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(GUIDE_OPEN_BROWSER_TAG),
                ) {
                    Text(stringResource(R.string.guide_open_browser))
                }
                Spacer(Modifier.height(UmamiSpacing.Large))
            }
        }
        // As on the tea's detail: over the hero, on its own surface, it stays put while the page scrolls.
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

/** The shop's page of the guide [slug], on the host of [articleUrl] (a guide's own page). */
private fun shopGuidePage(articleUrl: String, slug: String): String? = try {
    val uri = URI(articleUrl)
    URI(uri.scheme, uri.authority, "/tee-info/$slug", null, null).toString()
} catch (_: URISyntaxException) {
    null
}

private const val HEADER_RATIO = 2.4f
private const val HERO_RATIO = 16f / 9f
private const val BACK_SURFACE_ALPHA = 0.92f
private val BACK_ROW_HEIGHT = 56.dp
