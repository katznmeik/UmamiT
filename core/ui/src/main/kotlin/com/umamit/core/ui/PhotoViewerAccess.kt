package com.umamit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * How a hero or a photo field reaches the full-screen photo viewer (SCR-28, TEA-05, D-114 Q9) without knowing
 * about navigation: the app provides it ([LocalPhotoViewer]) and routes [open] above the shell, gated on an alert
 * (rule 8). The viewer reports the photo it ended on ([report]); the hero that opened it follows ([viewed],
 * [consume]).
 */
@Stable
interface PhotoViewerAccess {
    /** Opens the viewer for [owner] on [sources] (app-private file paths or picked content URIs) at [index]. */
    fun open(owner: String, sources: List<String>, index: Int)

    /** The photo [owner]'s viewer last showed, or null when none is waiting to be followed. */
    fun viewed(owner: String): Int?

    /** The viewer of [owner] shows the photo [index]. */
    fun report(owner: String, index: Int)

    /** [owner] has followed the viewer; [viewed] is null again. */
    fun consume(owner: String)
}

/** No viewer: a tap opens nothing (previews, tests of a screen alone). */
object NoPhotoViewer : PhotoViewerAccess {
    override fun open(owner: String, sources: List<String>, index: Int) = Unit
    override fun viewed(owner: String): Int? = null
    override fun report(owner: String, index: Int) = Unit
    override fun consume(owner: String) = Unit
}

/** The viewer the app routes to; [NoPhotoViewer] where none is provided. */
@Suppress("CompositionLocalAllowlist") // one app-wide service, so the heroes need no host plumbing
val LocalPhotoViewer = compositionLocalOf<PhotoViewerAccess> { NoPhotoViewer }

/**
 * One hero's (or photo field's) way to the viewer: its own [owner] id, kept across recreation, so the viewer's last
 * photo finds its way back to this hero and no other.
 */
@Stable
class PhotoViewerOpener internal constructor(private val owner: String, private val access: PhotoViewerAccess) {
    /** Opens the viewer on [sources] at [index]. */
    fun open(sources: List<String>, index: Int) = access.open(owner, sources, index)

    /** The photo the viewer last showed for this hero, until [consume]d. */
    val viewed: Int? get() = access.viewed(owner)

    /** The hero follows the viewer: [viewed] is null afterwards. */
    fun consume() = access.consume(owner)
}

/** The [PhotoViewerOpener] of the composable it is called in. */
@Composable
fun rememberPhotoViewerOpener(): PhotoViewerOpener {
    val access = LocalPhotoViewer.current
    val owner = rememberSaveable { UUID.randomUUID().toString() }
    return remember(access, owner) { PhotoViewerOpener(owner, access) }
}

/**
 * The access the app's root provides: [open] starts the viewer route; the photo the viewer ended on survives a
 * configuration change.
 */
@Composable
fun rememberPhotoViewerAccess(open: (owner: String, sources: List<String>, index: Int) -> Unit): PhotoViewerAccess {
    var owner by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val currentOpen by rememberUpdatedState(open)
    return remember {
        object : PhotoViewerAccess {
            override fun open(owner: String, sources: List<String>, index: Int) = currentOpen(owner, sources, index)

            override fun viewed(owner: String): Int? = if (owner == this.reportedOwner) reportedIndex else null

            override fun report(owner: String, index: Int) {
                reportedOwner = owner
                reportedIndex = index
            }

            override fun consume(owner: String) {
                if (owner == reportedOwner) reportedOwner = null
            }

            private var reportedOwner: String?
                get() = owner
                set(value) {
                    owner = value
                }

            private var reportedIndex: Int
                get() = index
                set(value) {
                    index = value
                }
        }
    }
}
