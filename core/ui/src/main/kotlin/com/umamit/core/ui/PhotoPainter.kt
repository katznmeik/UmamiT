package com.umamit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.umamit.core.designsystem.theme.UmamiTheme
import java.io.File

/**
 * The painter of a tea photo stored at [path] (05 §3.9, an app-private file),
 * or null without one, so the caller shows the leaf placeholder (03 §3.5).
 * Coil loads it off the main thread; only local files are read (06 §4).
 */
@Composable
fun rememberPhotoPainter(path: String?): Painter? = path?.let {
    rememberAsyncImagePainter(model = photoRequest(File(it)), contentScale = ContentScale.Crop)
}

/**
 * The painter of an image the photo picker just returned as [uri] (a content URI), before it is processed and
 * stored (SCR-14). Coil loads it off the main thread.
 */
@Composable
fun rememberPickedPhotoPainter(uri: String): Painter =
    rememberAsyncImagePainter(model = photoRequest(uri), contentScale = ContentScale.Crop)

/**
 * The painter of a tea photo stored at [path], showing [fallback] without one, while it
 * loads and when it cannot be read, so a layout that reserves the photo's space never
 * depends on whether or when the file arrives (SCR-05, D-058).
 */
@Composable
fun rememberPhotoPainter(path: String?, fallback: Painter): Painter = if (path == null) {
    fallback
} else {
    rememberAsyncImagePainter(
        model = photoRequest(File(path)),
        contentScale = ContentScale.Crop,
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
    )
}

/**
 * The painter of a photo in the full-screen viewer (SCR-28): the stored file at [source], or an image the photo picker
 * returned (a content URI, still unsaved, SCR-14). Fitted whole, never cropped; Coil reads the EXIF rotation, and a
 * stored photo is upright already (#58). Null while it has no source.
 */
@Composable
fun rememberViewerPainter(source: String): Painter {
    val data: Any = if ("://" in source) source else File(source)
    return rememberAsyncImagePainter(model = photoRequest(data), contentScale = ContentScale.Fit)
}

/** A request for [data] that fades in (200 ms) unless the user asked for reduced motion. */
@Composable
private fun photoRequest(data: Any): ImageRequest = ImageRequest.Builder(LocalContext.current)
    .data(data)
    .crossfade(if (UmamiTheme.reduceMotion) 0 else FADE_MS)
    .build()

private const val FADE_MS = 200
