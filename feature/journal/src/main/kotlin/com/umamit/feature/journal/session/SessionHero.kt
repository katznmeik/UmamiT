package com.umamit.feature.journal.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Photo
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedDropdownMenu
import com.umamit.core.ui.HeroAddPhoto
import com.umamit.core.ui.HeroImage
import com.umamit.core.ui.HeroLeaf
import com.umamit.core.ui.HeroPlaceholder
import com.umamit.core.ui.PhotoHero
import com.umamit.core.ui.rememberAlertGate
import com.umamit.core.ui.rememberPhotoViewerOpener
import com.umamit.feature.journal.R

/** The hero's pager over the session's photos (SCR-16). */
const val SESSION_PHOTOS_TAG = "session_photos"

/** The hero's tea cover, shown while the session has no photos of its own. */
const val SESSION_HERO_COVER_TAG = "session_hero_cover"

/** The hero's type leaf, without session photos and tea cover. */
const val SESSION_HERO_LEAF_TAG = "session_hero_leaf"

/** The hero's tinted box while it doesn't know yet what to show (no leaf, no controls). */
const val SESSION_HERO_LOADING_TAG = "session_hero_loading"

/** *+ Foto* over the hero. */
const val SESSION_ADD_PHOTO_TAG = "session_add_photo"

/** The shown photo's ⋮ over the hero. */
const val SESSION_PHOTO_MORE_TAG = "session_photo_more"

/** A photo of the session's hero (JRN-07): its id and its file. */
data class SessionHeroPhoto(val id: String, val path: String)

/**
 * What the session page's hero shows (SCR-16, JRN-07, D-112).
 *
 * @property photos the session's photos whose files exist, in the order added.
 * @property photoCount the session's photo rows, for the limit of [Photo.MAX_PER_SESSION].
 * @property coverPath the tea's cover file, shown without session photos; null for a tea without one or deleted.
 */
data class SessionHero(
    val photos: List<SessionHeroPhoto> = emptyList(),
    val photoCount: Int = photos.size,
    val coverPath: String? = null,
) {
    /** *+ Foto* is greyed out at 6 photos („Höchstens 6 Fotos", JRN-07). */
    val canAddPhoto: Boolean get() = photoCount < Photo.MAX_PER_SESSION
}

/** What the hero does: *+ Foto* opens the photo picker; the confirmed *Löschen* removes the photo by its id. */
class SessionHeroActions(val addPhoto: () -> Unit = {}, val deletePhoto: (photoId: String) -> Unit = {})

/**
 * The session page laid out under its hero, as the tea detail (SCR-09): [hero] at the top under the status bar,
 * then [content], all scrolling; ← and the page's ⋮ ([menu], null for none) stay put over the photo, each on the
 * translucent surface that keeps it legible on a bright photo.
 */
@Composable
internal fun SessionHeroLayout(
    onBack: () -> Unit,
    menu: (@Composable () -> Unit)?,
    hero: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(CONTENT_INSETS)),
        ) {
            hero()
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_ACTIONS_INSETS))
                .padding(UmamiSpacing.XSmall),
        ) {
            OverPhoto(Modifier.align(Alignment.TopStart)) { BackAction(onBack) }
            if (menu != null) OverPhoto(Modifier.align(Alignment.TopEnd)) { menu() }
        }
    }
}

/**
 * The session page's hero (SCR-16, JRN-07, D-112), 4:3 like the tea detail's: with session photos a pager over them
 * in the order added, dots from two on; without, the tea's cover, and without that the type leaf (a deleted tea: the
 * leaf). *+ Foto* (only for a finished session, [canAdd]) opens the photo picker; at 6 it is greyed out with
 * „Höchstens 6 Fotos". The shown photo's ⋮ („Foto-Optionen", only with session photos) offers *Löschen* after „Foto
 * löschen?"; the menu and the question close in the frame an alert starts ([alertKey], rule 8). TalkBack reads
 * „Fotos der Sitzung" and each photo as „Foto 1 von 3". While [hero] is still loading (null) only the 4:3 tinted
 * box shows, so a session with photos or a cover never flashes the leaf first.
 */
@Composable
@Suppress("LongParameterList") // the hero's state, its actions and the painter seam
internal fun SessionHeroPager(
    hero: SessionHero?,
    styleKey: String?,
    canAdd: Boolean,
    actions: SessionHeroActions,
    alertKey: String?,
    photo: @Composable (path: String) -> Painter?,
) {
    val color = UmamiTheme.extendedColors.teaTypes.forStyle(styleKey)
    if (hero == null) {
        HeroPlaceholder(color, Modifier.testTag(SESSION_HERO_LOADING_TAG))
        return
    }
    val viewer = rememberPhotoViewerOpener()
    PhotoHero(
        photos = hero.photos,
        key = SessionHeroPhoto::id,
        path = SessionHeroPhoto::path,
        description = { _, index, count -> stringResource(R.string.session_photo_description, index + 1, count) },
        pagerDescription = stringResource(R.string.session_photos_description),
        pagerTag = SESSION_PHOTOS_TAG,
        placeholderColor = color,
        photo = photo,
        empty = {
            val cover = hero.coverPath?.let { photo(it) }
            if (cover != null) {
                // The tea's cover opens the viewer too, as the one photo (SCR-16, SCR-28).
                HeroImage(
                    cover,
                    color,
                    Modifier.testTag(SESSION_HERO_COVER_TAG),
                    onClick = { hero.coverPath?.let { viewer.open(listOf(it), 0) } },
                )
            } else {
                HeroLeaf(color, Modifier.testTag(SESSION_HERO_LEAF_TAG))
            }
        },
    ) { current ->
        current?.let { PhotoMenu(it, alertKey, actions.deletePhoto) }
        if (canAdd) {
            HeroAddPhoto(
                label = stringResource(R.string.session_photo_add),
                limitLabel = stringResource(R.string.session_photo_limit),
                enabled = hero.canAddPhoto,
                onClick = actions.addPhoto,
                buttonModifier = Modifier.testTag(SESSION_ADD_PHOTO_TAG),
            )
        }
    }
}

/** The shown photo's ⋮ with *Löschen*; the photo is fixed when the menu opens, so a moving page never changes it. */
@Composable
private fun PhotoMenu(current: SessionHeroPhoto, alertKey: String?, onDelete: (photoId: String) -> Unit) {
    val menu = rememberAlertGate(alertKey)
    val deleteAsk = rememberAlertGate(alertKey)
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    Box {
        OverPhoto {
            IconButton(
                onClick = {
                    target = current.id
                    menu.open()
                },
                modifier = Modifier.testTag(SESSION_PHOTO_MORE_TAG),
            ) {
                Icon(
                    painterResource(UmamiIcons.MoreVert),
                    contentDescription = stringResource(R.string.session_photo_more),
                )
            }
        }
        AlertGatedDropdownMenu(menu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_photo_delete)) },
                onClick = {
                    menu.close()
                    deleteAsk.open()
                },
            )
        }
    }
    AlertGated(deleteAsk) {
        AlertDialog(
            onDismissRequest = deleteAsk::close,
            title = { Text(stringResource(R.string.session_photo_delete_title)) },
            text = { Text(stringResource(R.string.session_photo_delete_body)) },
            dismissButton = {
                TextButton(onClick = deleteAsk::close) { Text(stringResource(R.string.session_photo_delete_cancel)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAsk.close()
                        target?.let(onDelete)
                    },
                ) { Text(stringResource(R.string.session_photo_delete_confirm)) }
            },
        )
    }
}

/** The content keeps clear of the side cut-outs and the gesture handle; the hero goes under the status bar. */
private val CONTENT_INSETS = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
private val TOP_ACTIONS_INSETS = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
