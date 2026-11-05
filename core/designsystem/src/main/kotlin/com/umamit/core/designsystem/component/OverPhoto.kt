package com.umamit.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A round control over a photo or a hero band (the back arrow, the overflow menu): a translucent surface that keeps
 * it legible on any photo. [contentColor] is set explicitly: the translucent colour matches no scheme colour, so
 * `contentColorFor` would not find its ink and an icon would be dark on dark (F9).
 */
@Composable
fun OverPhoto(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = OVER_PHOTO_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        content = content,
    )
}

private const val OVER_PHOTO_ALPHA = 0.92f
