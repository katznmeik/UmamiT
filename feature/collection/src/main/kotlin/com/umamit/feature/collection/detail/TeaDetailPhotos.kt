package com.umamit.feature.collection.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.collection.R

/** The hero's photo pager (SCR-28). */
const val TEA_DETAIL_PHOTOS_TAG = "tea_detail_photos"

/** *+ Foto* over the hero. */
const val TEA_DETAIL_ADD_PHOTO_TAG = "tea_detail_add_photo"

/**
 * The detail's hero (SCR-09, SCR-28, TEA-05): a pager over the tea's photos, the cover first, with dots at its
 * lower start when there is more than one; without photos the style's leaf. *+ Foto* sits at its lower end and
 * opens the photo picker; at 10 photos it is greyed out with „Höchstens 10 Fotos". A newly added photo is shown at
 * once.
 * TalkBack reads „Fotos von *Tee*" and each photo as „Foto 1 von 3, Titelbild".
 */
@Composable
internal fun PhotoPager(
    tea: TeaDetail,
    placeholderColor: Color,
    photo: @Composable (path: String) -> Painter?,
    onAddPhoto: () -> Unit,
) {
    val photos = tea.photos
    val pager = rememberPagerState { photos.size.coerceAtLeast(1) }
    // An added photo goes after the others: show it.
    val shown = remember { mutableIntStateOf(photos.size) }
    LaunchedEffect(photos.size) {
        if (photos.size > shown.intValue && shown.intValue > 0) pager.scrollToPage(photos.lastIndex)
        shown.intValue = photos.size
    }
    val pagerDescription = stringResource(R.string.collection_tea_photos_description, tea.name)
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT_RATIO)) {
        if (photos.isEmpty()) {
            Placeholder(placeholderColor)
        } else {
            HorizontalPager(
                state = pager,
                key = { photos.getOrNull(it)?.id ?: it },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = pagerDescription }
                    .testTag(TEA_DETAIL_PHOTOS_TAG),
            ) { page ->
                PagerPhoto(photos[page], page, photos.size, placeholderColor, photo)
            }
        }
        // Dots at the start, *+ Foto* at the end: side by side, never over each other (font 2.0, short screens).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(UmamiSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photos.size > 1) Dots(count = photos.size, current = pager.currentPage) else Spacer(Modifier)
            AddPhoto(enabled = tea.canAddPhoto, onClick = onAddPhoto)
        }
    }
}

@Composable
private fun PagerPhoto(
    item: DetailPhoto,
    page: Int,
    count: Int,
    placeholderColor: Color,
    photo: @Composable (path: String) -> Painter?,
) {
    val label = if (item.isCover) {
        R.string.collection_tea_photo_cover_description
    } else {
        R.string.collection_tea_photo_description
    }
    val description = stringResource(label, page + 1, count)
    val painter = photo(item.path)
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize().semantics { contentDescription = description }) { Placeholder(placeholderColor) }
    }
}

/** The style's leaf on its tint, for a tea without photos. */
@Composable
private fun Placeholder(color: Color) {
    Box(
        modifier = Modifier.fillMaxSize().background(color.copy(alpha = PLACEHOLDER_ALPHA)),
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
                val color = if (index == current) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = IDLE_DOT_ALPHA)
                }
                Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
            }
        }
    }
}

/** *+ Foto*; greyed out with „Höchstens 10 Fotos" at the limit (TEA-05). */
@Composable
private fun AddPhoto(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OverPhoto(modifier, shape = RoundedCornerShape(ADD_PHOTO_CORNER)) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(ADD_PHOTO_CORNER),
            modifier = Modifier.testTag(TEA_DETAIL_ADD_PHOTO_TAG),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.collection_tea_photo_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    },
                )
                if (!enabled) {
                    Text(
                        text = stringResource(R.string.collection_tea_photo_limit),
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
