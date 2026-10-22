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
