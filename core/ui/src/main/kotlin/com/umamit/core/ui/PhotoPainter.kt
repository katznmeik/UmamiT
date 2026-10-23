package com.umamit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.rememberAsyncImagePainter
import java.io.File

/**
 * The painter of a tea photo stored at [path] (05 §3.9, an app-private file),
 * or null without one, so the caller shows the leaf placeholder (03 §3.5).
 * Coil loads it off the main thread; only local files are read (06 §4).
 */
@Composable
fun rememberPhotoPainter(path: String?): Painter? = path?.let { rememberAsyncImagePainter(model = File(it)) }

/**
 * The painter of a tea photo stored at [path], showing [fallback] without one, while it
 * loads and when it cannot be read, so a layout that reserves the photo's space never
 * depends on whether or when the file arrives (SCR-05, D-058).
 */
@Composable
fun rememberPhotoPainter(path: String?, fallback: Painter): Painter = if (path == null) {
    fallback
} else {
    rememberAsyncImagePainter(model = File(path), placeholder = fallback, error = fallback, fallback = fallback)
}
