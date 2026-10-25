package com.umamit.feature.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.SessionRepository
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.SavedSheetRequests
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long the sheet stays (SCR-06); TalkBack users may get longer (the system's recommended timeout). */
internal const val SAVED_SHEET_MILLIS = 10_000L

private val SheetCorner = 28.dp
private val SheetMaxWidth = 560.dp
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private const val HANDLE_ALPHA = 0.4f

/** A drag further down than this, or a fling faster than [DISMISS_VELOCITY], dismisses the sheet. */
private val DismissDistance = 48.dp
private const val DISMISS_VELOCITY = 1_000f

/** Dragged up, the sheet follows a fifth of the finger: friction instead of a hard stop. */
private const val UPWARD_FRICTION = 0.2f

/** What the sheet shows for one finished session. */
private data class SavedSheetContent(val sessionId: String, val summary: SavedSummary)

/**
 * The „Gespeichert" sheet (SCR-06, TMR-12): „Gespeichert im Journal · 3
 * Aufgüsse · 2:20" after *Sitzung beenden*, without a scrim: ignoring it is
 * fine. It leaves after 10 s, on a swipe down, or at once when an alert
 * starts. While [alerting] it is not composed at all, so no exit animation
 * runs over the alert, and a sheet due then is dropped (rule 8, D-061).
 * *Notizen hinzufügen* and PRF-07 come with M4.
 *
 * @param windowInsets the insets below it that nothing else has applied (the rail layout's bottom).
 */
@Composable
fun SavedSheetHost(
    requests: SavedSheetRequests,
    sessions: SessionRepository,
    alerting: Boolean,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0),
) {
    val pending by requests.pending.collectAsStateWithLifecycle()
    val id = pending
    LaunchedEffect(alerting, id) {
        if (alerting && id != null) requests.dismiss(id)
    }
    if (alerting) return

    val timeout = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(SAVED_SHEET_MILLIS, containsText = true)
        ?: SAVED_SHEET_MILLIS
    val content by rememberSavedSheetContent(id, requests, sessions, timeout)
    val visible = id != null && content?.sessionId == id
    // A finger on the sheet holds it: it never leaves under the finger. Until then the request's own
    // time runs (also while the shell was covered); after a release the full time starts again.
    var held by remember(id) { mutableStateOf(false) }
    var released by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, visible, held) {
        if (visible && !held && id != null) {
            delay(if (released) timeout else requests.remainingMs(id, timeout))
            requests.dismiss(id)
        }
    }

    val motion = UmamiTheme.motion
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(motion.sheet()) { it } + fadeIn(motion.fade()),
            exit = slideOutVertically(motion.sheet()) { it } + fadeOut(motion.exitFade()),
        ) {
            content?.let { shown ->
                SavedSheet(
                    content = shown,
                    onDismiss = { requests.dismiss(shown.sessionId) },
                    onHold = { pressed ->
                        held = pressed
                        if (!pressed) released = true
                    },
                    windowInsets = windowInsets,
                )
            }
        }
    }
}

/**
 * The content of the sheet for [id], kept while the sheet leaves. A request whose time ran out
 * while no shell showed it (the timer covered it, the app was away) is dropped, never shown late.
 */
@Composable
private fun rememberSavedSheetContent(
    id: String?,
    requests: SavedSheetRequests,
    sessions: SessionRepository,
    timeout: Long,
): State<SavedSheetContent?> = produceState(null, id) {
    if (id != null) {
        val session = sessions.getSession(id)
        if (session == null || requests.remainingMs(id, timeout) <= 0) {
            requests.dismiss(id)
        } else {
            value = SavedSheetContent(id, savedSummary(session))
        }
    }
}

@Composable
private fun SavedSheet(
    content: SavedSheetContent,
    onDismiss: () -> Unit,
    onHold: (Boolean) -> Unit,
    windowInsets: WindowInsets,
) {
    val motion = UmamiTheme.motion
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnHold by rememberUpdatedState(onHold)
    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { DismissDistance.toPx() }
    // Follows the finger down, and up only with friction; a short drag springs back, critically damped.
    val drag = remember(content.sessionId) { Animatable(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }

    val title = stringResource(R.string.journal_saved_title)
    val infusions = pluralStringResource(
        R.plurals.journal_infusions,
        content.summary.infusions,
        content.summary.infusions,
    )
    val summaryDescription = stringResource(
        R.string.journal_saved_summary_description,
        infusions,
        spokenDuration(content.summary.steepSeconds),
    )
    val dismissLabel = stringResource(R.string.journal_saved_dismiss)

    Surface(
        modifier = Modifier
            .widthIn(max = SheetMaxWidth)
            .fillMaxWidth()
            .onSizeChanged { sheetHeight = it.height }
            .offset { IntOffset(0, drag.value.roundToInt()) }
            // The press itself holds the sheet, before any movement: a resting finger too (D-061).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnHold(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        currentOnHold(false)
                    }
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        val next = drag.value + delta
                        drag.snapTo(if (next < 0f) drag.value + delta * UPWARD_FRICTION else next)
                    }
                },
                onDragStopped = { velocity ->
                    if (drag.value > dismissDistance || velocity > DISMISS_VELOCITY) {
                        // The fling carries on at its own speed, then the sheet is gone.
                        drag.animateTo(sheetHeight.toFloat(), motion.sheet(), initialVelocity = velocity)
                        currentOnDismiss()
                    } else {
                        drag.animateTo(0f, motion.sheet(), initialVelocity = velocity)
                    }
                },
            )
            .semantics(mergeDescendants = true) {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) {
                        currentOnDismiss()
                        true
                    },
                )
            },
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
        // Sheets are `surface` (03 §3.1), untinted.
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Flat like the rest (03 §3.3): a hairline instead of a shadow sets the sheet off the page.
        border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = UmamiSpacing.SideMargin)
                .padding(top = UmamiSpacing.XSmall, bottom = UmamiSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = HandleWidth, height = HandleHeight)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA),
                        RoundedCornerShape(HandleHeight),
                    ),
            )
            Spacer(Modifier.height(UmamiSpacing.Medium))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(UmamiIcons.Check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(UmamiSpacing.Small))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            R.string.journal_saved_summary,
                            infusions,
                            clockText(content.summary.steepSeconds),
                        ),
                        modifier = Modifier.semantics { contentDescription = summaryDescription },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
