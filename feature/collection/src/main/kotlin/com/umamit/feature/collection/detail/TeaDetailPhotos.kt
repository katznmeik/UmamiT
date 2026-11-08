package com.umamit.feature.collection.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.collection.R

/** The hero's photo pager (SCR-28). */
const val TEA_DETAIL_PHOTOS_TAG = "tea_detail_photos"

/** *+ Foto* over the hero. */
const val TEA_DETAIL_ADD_PHOTO_TAG = "tea_detail_add_photo"

/** The shown photo's ⋮ over the hero. */
const val TEA_DETAIL_PHOTO_MORE_TAG = "tea_detail_photo_more"

/**
 * The detail's hero (SCR-09, SCR-28, TEA-05): a pager over the tea's photos, the cover first, with dots at its
 * lower start when there is more than one; without photos the style's leaf. *+ Foto* sits at its lower end and
 * opens the photo picker; at 10 photos it is greyed out with „Höchstens 10 Fotos". A newly added photo is shown at
 * once. The shown photo's ⋮ („Foto-Optionen") offers *Als Titelbild* (not on the cover) and *Löschen*, which asks
 * first (TEA-05, #186); the menu and the dialog close in the frame an alert starts ([alertKey], rule 8).
 * TalkBack reads „Fotos von *Tee*" and each photo as „Foto 1 von 3, Titelbild".
 */
@Composable
@Suppress("LongParameterList") // the pager's photos and its three actions
internal fun PhotoPager(
    tea: TeaDetail,
    placeholderColor: Color,
    photo: @Composable (path: String) -> Painter?,
    alertKey: String?,
    onAddPhoto: () -> Unit,
    onSetCover: (photoId: String) -> Unit,
    onDelete: (photoId: String) -> Unit,
) {
    val photos = tea.photos
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
    val pagerDescription = stringResource(R.string.collection_tea_photos_description, tea.name)
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT_RATIO)) {
        if (photos.isEmpty()) {
            Placeholder(placeholderColor)
        } else {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                // Keyed by photo: after *Als Titelbild* the page follows its photo to the front, nothing jumps (#186).
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                photos.getOrNull(pager.currentPage)?.let { current ->
                    PhotoMenu(current, photos.size, alertKey, onSetCover, onDelete)
                }
                AddPhoto(enabled = tea.canAddPhoto, onClick = onAddPhoto)
            }
        }
    }
}

/**
 * The shown photo's ⋮: *Als Titelbild* unless it is the cover, and *Löschen* after „Foto löschen?". The photo is
 * fixed when the menu opens, so a page that moves meanwhile never changes which photo is meant.
 */
@Composable
private fun PhotoMenu(
    current: DetailPhoto,
    count: Int,
    alertKey: String?,
    onSetCover: (photoId: String) -> Unit,
    onDelete: (photoId: String) -> Unit,
) {
    val menu = rememberAlertGate(alertKey)
    val deleteAsk = rememberAlertGate(alertKey)
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var targetIsCover by rememberSaveable { mutableStateOf(false) }
    Box {
        OverPhoto {
            IconButton(
                onClick = {
                    target = current.id
                    targetIsCover = current.isCover
                    menu.open()
                },
                modifier = Modifier.testTag(TEA_DETAIL_PHOTO_MORE_TAG),
            ) {
                Icon(
                    painterResource(UmamiIcons.MoreVert),
                    contentDescription = stringResource(R.string.collection_tea_photo_more),
                )
            }
        }
        AlertGatedDropdownMenu(menu) {
            if (!targetIsCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_tea_photo_set_cover)) },
                    onClick = {
                        menu.close()
                        target?.let(onSetCover)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collection_tea_photo_delete)) },
                onClick = {
                    menu.close()
                    deleteAsk.open()
                },
            )
        }
    }
    AlertGated(deleteAsk) {
        DeletePhotoDialog(
            nextBecomesCover = targetIsCover && count > 1,
            onDelete = {
                deleteAsk.close()
                target?.let(onDelete)
            },
            onDismiss = deleteAsk::close,
        )
    }
}

/** „Foto löschen?" · *Abbrechen* / *Löschen* (TEA-05). */
@Composable
private fun DeletePhotoDialog(nextBecomesCover: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_tea_photo_delete_title)) },
        text = {
            Text(
                stringResource(
                    if (nextBecomesCover) {
                        R.string.collection_tea_photo_delete_cover_body
                    } else {
                        R.string.collection_tea_photo_delete_body
                    },
                ),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.collection_tea_photo_delete_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.collection_tea_photo_delete_confirm)) }
        },
    )
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
            modifier = Modifier.fillMaxSize().background(placeholderColor.copy(alpha = PLACEHOLDER_ALPHA)),
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
