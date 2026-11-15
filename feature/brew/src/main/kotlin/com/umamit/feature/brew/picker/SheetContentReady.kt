package com.umamit.feature.brew.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Whether a modal sheet that slides in may compose its content: false in the first frame of its window, true from
 * the next. That first frame, which also creates the window, then holds only the sheet's container, still below
 * the screen where the slide starts; the content follows a frame later, before the sheet has moved into view, and
 * the slide goes on to the sheet's full height (NFR-PERF-12: the Teeauswahl). Under
 * [reduceMotion] the sheet appears expanded without a slide, so its content is there at once.
 */
@Composable
internal fun rememberSheetContentReady(reduceMotion: Boolean): Boolean {
    var ready by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!ready) {
            withFrameNanos {}
            ready = true
        }
    }
    return ready || reduceMotion
}
