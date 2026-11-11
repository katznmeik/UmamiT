package com.umamit.feature.collection.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.HeroAddPhoto
import com.umamit.core.ui.PhotoHero
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
    PhotoHero(
        photos = photos,
        key = DetailPhoto::id,
        path = DetailPhoto::path,
        description = { item, index, count ->
            val label = if (item.isCover) {
                R.string.collection_tea_photo_cover_description
            } else {
                R.string.collection_tea_photo_description
            }
            stringResource(label, index + 1, count)
        },
        pagerDescription = stringResource(R.string.collection_tea_photos_description, tea.name),
        pagerTag = TEA_DETAIL_PHOTOS_TAG,
        placeholderColor = placeholderColor,
        photo = photo,
    ) { current ->
        current?.let { PhotoMenu(it, photos.size, alertKey, onSetCover, onDelete) }
        HeroAddPhoto(
            label = stringResource(R.string.collection_tea_photo_add),
            limitLabel = stringResource(R.string.collection_tea_photo_limit),
            enabled = tea.canAddPhoto,
            onClick = onAddPhoto,
            buttonModifier = Modifier.testTag(TEA_DETAIL_ADD_PHOTO_TAG),
        )
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
