package com.umamit.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.AccessibilityManager
import kotlinx.coroutines.withTimeoutOrNull

/** About 8 s for *Rückgängig* (D-093 Q21). */
private const val UNDO_TIMEOUT_MS = 8_000L

/**
 * Shows [message] with the action [undoLabel] for about 8 s (D-093 Q21), longer when the [accessibility] settings
 * ask for more time, and says whether the action was tapped; false when it timed out or was dismissed.
 */
suspend fun SnackbarHostState.showUndo(
    message: String,
    undoLabel: String,
    accessibility: AccessibilityManager?,
): Boolean {
    val timeoutMs = accessibility?.calculateRecommendedTimeoutMillis(
        UNDO_TIMEOUT_MS,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: UNDO_TIMEOUT_MS
    val result = withTimeoutOrNull(timeoutMs) {
        showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    }
    return result == SnackbarResult.ActionPerformed
}
