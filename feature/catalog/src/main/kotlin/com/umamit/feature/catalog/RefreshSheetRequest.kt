package com.umamit.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * A tap on a refresh notification (SCR-22, D-097), each [request] token once, also across a
 * recreation, and only once no alert plays (rule 8): [recall] fetches the last result from the
 * refresh log when the refresh holds nothing, then [open] gets whether the sheet has something to
 * show ([showing]; nothing after a cancelled refresh, so no sheet waits for the next one).
 */
@Composable
internal fun OnRefreshSheetRequest(
    request: Long,
    alerting: Boolean,
    recall: suspend () -> Unit,
    showing: () -> Boolean,
    open: (Boolean) -> Unit,
) {
    var handled by rememberSaveable { mutableLongStateOf(0L) }
    val currentRecall by rememberUpdatedState(recall)
    val currentShowing by rememberUpdatedState(showing)
    val currentOpen by rememberUpdatedState(open)
    LaunchedEffect(request, alerting) {
        if (request == 0L || request == handled || alerting) return@LaunchedEffect
        handled = request
        currentRecall()
        currentOpen(currentShowing())
    }
}
