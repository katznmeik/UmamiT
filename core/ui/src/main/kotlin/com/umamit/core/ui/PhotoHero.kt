package com.umamit.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * A 4:3 photo hero (SCR-28 for a tea, SCR-16 for a session): a pager over [photos] keyed by [key], with dots at its
 * lower start from two photos on; without photos [empty] (the style's leaf by default). [controls] sit at its lower
 * end, given the photo shown (null without photos): the photo's ⋮ and *+ Foto* ([HeroAddPhoto]). A newly added photo
 * goes last and the pager glides to it. TalkBack reads [pagerDescription] for the pager and [description] for each
 * photo; a photo whose file is missing ([photo] null) shows the leaf.
 */
@Composable
@Suppress("LongParameterList") // the photos, how each is read, and the hero's slots
fun <T> PhotoHero(
    photos: List<T>,
    key: (T) -> String,
    path: (T) -> String,
    description: @Composable (item: T, index: Int, count: Int) -> String,
    pagerDescription: String,
    pagerTag: String,
    placeholderColor: Color,
    photo: @Composable (path: String) -> Painter?,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit = { HeroLeaf(placeholderColor) },
    controls: @Composable RowScope.(current: T?) -> Unit,
) {
    val pager = rememberPagerState { photos.size.coerceAtLeast(1) }
    // An added photo goes after the others: glide to it (from the page before it when further away).
    val shown = remember { mutableIntStateOf(photos.size) }
    val reduceMotion = UmamiTheme.reduceMotion
    LaunchedEffect(photos.size) {
        if (photos.size > shown.intValue && shown.intValue > 0) {
            if (pager.currentPage < photos.lastIndex - 1) pager.scrollToPage(photos.lastIndex - 1)
            if (reduceMotion) {
                pager.scrollToPage(photos.lastIndex)
            } else {
                pager.animateScrollToPage(photos.lastIndex, animationSpec = spring(dampingRatio = 1f, stiffness = 320f))
            }
        }
        shown.intValue = photos.size
    }
    Box(modifier = modifier.fillMaxWidth().aspectRatio(HERO_ASPECT_RATIO)) {
        if (photos.isEmpty()) {
            empty()
        } else {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                // Keyed by photo: a photo that moves keeps its page, nothing jumps (#186).
                key = { photos.getOrNull(it)?.let(key) ?: it },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = pagerDescription }
                    .testTag(pagerTag),
            ) { page ->
                val item = photos[page]
                PagerPhoto(path(item), description(item, page, photos.size), placeholderColor, photo)
            }
        }
        // Dots at the start, the controls at the end: side by side, never over each other (font 2.0, short screens).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(UmamiSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photos.size > 1) Dots(count = photos.size, current = pager.currentPage) else Spacer(Modifier)
            Row(
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                controls(photos.getOrNull(pager.currentPage))
            }
        }
    }
}

@Composable
private fun PagerPhoto(
    path: String,
    description: String,
    placeholderColor: Color,
    photo: @Composable (path: String) -> Painter?,
) {
    val painter = photo(path)
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = description,
            modifier = Modifier.fillMaxSize().background(placeholderColor.copy(alpha = PLACEHOLDER_ALPHA)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize().semantics { contentDescription = description }) { HeroLeaf(placeholderColor) }
    }
}

/** A hero's still image (a session's tea cover, SCR-16): cropped on the style's tint, read by TalkBack as nothing. */
@Composable
fun HeroImage(painter: Painter, placeholderColor: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.fillMaxSize().background(placeholderColor.copy(alpha = PLACEHOLDER_ALPHA)),
        contentScale = ContentScale.Crop,
    )
}

/** The style's leaf on its tint, for a hero without photos. */
@Composable
fun HeroLeaf(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(color.copy(alpha = PLACEHOLDER_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UmamiIcons.Leaf),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(HERO_LEAF_SIZE),
        )
    }
}

/** One dot per photo, the shown one filled; TalkBack reads the photos themselves. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    OverPhoto(modifier.clearAndSetSemantics {}) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
        ) {
            repeat(count) { index ->
                val target = if (index == current) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = IDLE_DOT_ALPHA)
                }
                val color by animateColorAsState(target, UmamiTheme.motion.exitFade(), label = "dot")
                Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
            }
        }
    }
}

/**
 * *+ Foto* ([label]) over a hero's lower end, on the translucent surface of ← and ⋮; greyed out with [limitLabel]
 * („Höchstens 10 Fotos") when not [enabled]. [buttonModifier] goes on the button itself (a test tag).
 */
@Composable
fun HeroAddPhoto(
    label: String,
    limitLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    OverPhoto(modifier, shape = RoundedCornerShape(ADD_PHOTO_CORNER)) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(ADD_PHOTO_CORNER),
            modifier = buttonModifier,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    },
                )
                if (!enabled) {
                    Text(
                        text = limitLabel,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val HERO_ASPECT_RATIO = 4f / 3f
private const val PLACEHOLDER_ALPHA = 0.22f
private const val IDLE_DOT_ALPHA = 0.38f
private const val DISABLED_ALPHA = 0.38f
private val HERO_LEAF_SIZE = 64.dp
private val DOT_SIZE = 6.dp
private val DOT_GAP = 6.dp
private val ADD_PHOTO_CORNER = 20.dp
