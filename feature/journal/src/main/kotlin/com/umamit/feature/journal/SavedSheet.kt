package com.umamit.feature.journal

import android.util.Log
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.umamit.core.data.OpenedDateChange
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TastingWrite
import com.umamit.core.data.TeaRepository
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.core.ui.RatingStars
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** How long the sheet stays (SCR-06); TalkBack users may get longer (the system's recommended timeout). */
internal const val SAVED_SHEET_MILLIS = 10_000L

private val SheetCorner = 28.dp
private val SheetMaxWidth = 560.dp
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp
private const val HANDLE_ALPHA = 0.4f

/**
 * An action line and the line that replaces it share this minimum height (a text button's), so the sheet
 * keeps its height when the action is tapped (motion review N2).
 */
private val ActionLineMinHeight = 40.dp

/** A drag further down than this, or a fling faster than [DISMISS_VELOCITY], dismisses the sheet. */
private val DismissDistance = 48.dp
private const val DISMISS_VELOCITY = 1_000f

/** Dragged up, the sheet follows a fifth of the finger: friction instead of a hard stop. */
private const val UPWARD_FRICTION = 0.2f

/**
 * What the sheet shows for one finished session.
 *
 * @property recipeCheckTeaId the session's tea when it is flagged „Rezept prüfen": the sheet offers *Rezept passt*.
 * @property openedDate the tea and its opened date before, when this session's completion set the date: the
 *   sheet says „Packung als geöffnet markiert" and offers *Rückgängig* (INV-06).
 */
private data class SavedSheetContent(
    val sessionId: String,
    val summary: SavedSummary,
    val recipeCheckTeaId: String? = null,
    val openedDate: OpenedDateChange? = null,
)

/**
 * The „Gespeichert" sheet (SCR-06, TMR-12): „Gespeichert im Journal · 3
 * Aufgüsse · 2:20" after *Sitzung beenden*, without a scrim: ignoring it is
 * fine. It leaves after 10 s, on a swipe down, or at once when an alert
 * starts. While [alerting] it is not composed at all, so no exit animation
 * runs over the alert, and a sheet due then is dropped (rule 8, D-061).
 * From v0.6.0 (D-112) it also holds the rating stars (JRN-02: a tap rates at once and restarts the 10 s) and
 * *Notizen hinzufügen*, which closes the sheet and opens the session's page. Everything on it is optional.
 *
 * For a session of a tea flagged „Rezept prüfen" it offers *Rezept passt*,
 * which clears that tea's flag and nothing else (CAT-05, D-073); it leaves
 * with the sheet when an alert starts.
 *
 * After the session whose completion set its tea's opened date, it says
 * „Packung als geöffnet markiert" with *Rückgängig*, which writes back the
 * date before, none or a date (INV-06, D-104). The write is never cancelled
 * by an alert either.
 *
 * @param sessions the finished session, and the opened date its completion set.
 * @param teas the session's tea, read when the sheet is due and cleared by *Rezept passt*.
 * @param tasting the rating write of the stars; a write already started is never cancelled (rule 8).
 * @param onOpenNotes opens the page of the session (SCR-16) above the current tab; the sheet closes with it.
 * @param windowInsets the insets below it that nothing else has applied (the rail layout's bottom).
 */
@Composable
fun SavedSheetHost(
    requests: SavedSheetRequests,
    sessions: SessionRepository,
    teas: TeaRepository,
    tasting: SessionTasting,
    onOpenNotes: (sessionId: String) -> Unit,
    alerting: Boolean,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0),
) {
    val pending by requests.pending.collectAsStateWithLifecycle()
    val id = pending
    // Above the early return: a *Rezept passt*, *Rückgängig* or rating write is never cancelled by an alert starting.
    val writes = rememberCoroutineScope()
    // Rating writes run one after another in tap order, so the last tap is the one stored (JRN-02).
    val ratingLock = remember { Mutex() }
    val currentOnOpenNotes by rememberUpdatedState(onOpenNotes)
    LaunchedEffect(alerting, id) {
        if (alerting && id != null) requests.dismiss(id)
    }
    if (alerting) return

    val timeout = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(SAVED_SHEET_MILLIS, containsText = true)
        ?: SAVED_SHEET_MILLIS
    val content by rememberSavedSheetContent(id, requests, sessions, teas, timeout)
    val visible = id != null && content?.sessionId == id
    // A finger on the sheet holds it: it never leaves under the finger. Until then the request's own
    // time runs (also while the shell was covered); after a release the full time starts again.
    var held by remember(id) { mutableStateOf(false) }
    var released by remember(id) { mutableStateOf(false) }
    // Each star tap starts the 10 s again, so a half step has time (SCR-06).
    var restarts by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, visible, held, restarts) {
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
                    onRecipeOk = { teaId ->
                        writes.launch { guarded("Rezept passt not written", Unit) { teas.clearRecipeCheck(teaId) } }
                    },
                    onUndoOpening = { change ->
                        writes.launch { guarded("Opened date not undone", Unit) { sessions.undoOpenedDate(change) } }
                    },
                    onRate = { rating ->
                        released = true
                        restarts++
                        writes.launch {
                            guarded("Rating not written", Unit) {
                                val result = ratingLock.withLock { tasting.setRating(shown.sessionId, rating) }
                                if (result != TastingWrite.SAVED) Log.w(TAG, "Rating not written: $result")
                            }
                        }
                    },
                    onNotes = {
                        requests.dismiss(shown.sessionId)
                        currentOnOpenNotes(shown.sessionId)
                    },
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
    teas: TeaRepository,
    timeout: Long,
): State<SavedSheetContent?> = produceState(null, id) {
    if (id != null) {
        val session = sessions.getSession(id)
        if (session == null || requests.remainingMs(id, timeout) <= 0) {
            requests.dismiss(id)
        } else {
            val flagged = session.snapshot.teaId?.takeIf { teas.getTea(it)?.recipeCheckReason != null }
            value = SavedSheetContent(
                id,
                savedSummary(session),
                recipeCheckTeaId = flagged,
                // A failed read offers no undo line; the sheet still shows.
                openedDate = guarded("Opened date change not read", null) { sessions.openedDateChange(id) },
            )
        }
    }
}

/**
 * [block], or [fallback] when it fails: the timer runs in this process, so a failed sheet write or read
 * fails that action, never the app (rule 8, D-090). Cancellation is passed on.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> guarded(what: String, fallback: T, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, what, e)
    fallback
}

private const val TAG = "SavedSheet"

@Composable
private fun SavedSheet(
    content: SavedSheetContent,
    onRecipeOk: (teaId: String) -> Unit,
    onUndoOpening: (OpenedDateChange) -> Unit,
    onRate: (Double?) -> Unit,
    onNotes: () -> Unit,
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
    // What the stars show: this sheet's own taps; the session starts unrated.
    var rating by remember(content.sessionId) { mutableStateOf<Double?>(null) }

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
            // The check stays at the title now that stars and actions make the column tall.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
                    // Its own merged node, so the sheet's merge does not swallow the stars' TalkBack adjust action.
                    Box(modifier = Modifier.padding(top = UmamiSpacing.XSmall).semantics(mergeDescendants = true) {}) {
                        RatingStars(
                            value = rating,
                            onChange = {
                                rating = it
                                onRate(it)
                            },
                        )
                    }
                    NotesAction(onNotes)
                    content.openedDate?.let { change -> OpenedUndo(content.sessionId) { onUndoOpening(change) } }
                    content.recipeCheckTeaId?.let { teaId -> RecipeOk(content.sessionId) { onRecipeOk(teaId) } }
                }
            }
        }
    }
}

/** *Notizen hinzufügen* (JRN-05, D-112): closes the sheet and opens the session's page, without the keyboard. */
@Composable
private fun NotesAction(onClick: () -> Unit) {
    // No side padding: its label lines up with the lines above.
    TextButton(
        modifier = Modifier.heightIn(min = ActionLineMinHeight),
        onClick = onClick,
        contentPadding = PaddingValues(
            vertical = ButtonDefaults.TextButtonContentPadding.calculateTopPadding(),
        ),
    ) {
        Text(stringResource(R.string.journal_saved_notes))
    }
}

/**
 * „Packung als geöffnet markiert" · *Rückgängig* (INV-06, D-104), then the line that says the date is back.
 * The text wraps beside the action at a large font.
 */
@Composable
private fun OpenedUndo(sessionId: String, onUndo: () -> Unit) {
    var done by remember(sessionId) { mutableStateOf(false) }
    val lineModifier = Modifier.padding(top = UmamiSpacing.XSmall).heightIn(min = ActionLineMinHeight)
    if (done) {
        DoneLine(stringResource(R.string.journal_saved_opened_undone), lineModifier)
    } else {
        Row(modifier = lineModifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.journal_saved_opened),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(UmamiSpacing.Small))
            TextButton(
                onClick = {
                    done = true
                    onUndo()
                },
            ) {
                Text(stringResource(R.string.journal_saved_opened_undo))
            }
        }
    }
}

/** *Rezept passt* (CAT-05, D-073), then the line that says the flag is gone. */
@Composable
private fun RecipeOk(sessionId: String, onClick: () -> Unit) {
    var done by remember(sessionId) { mutableStateOf(false) }
    val lineModifier = Modifier.heightIn(min = ActionLineMinHeight)
    if (done) {
        DoneLine(stringResource(R.string.journal_saved_recipe_ok_done), lineModifier)
    } else {
        // No side padding: its label lines up with the lines above.
        TextButton(
            modifier = lineModifier,
            onClick = {
                done = true
                onClick()
            },
            contentPadding = PaddingValues(
                vertical = ButtonDefaults.TextButtonContentPadding.calculateTopPadding(),
            ),
        ) {
            Text(stringResource(R.string.journal_saved_recipe_ok))
        }
    }
}

/** The line that says an action is done, centred in the action's height. */
@Composable
private fun DoneLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
