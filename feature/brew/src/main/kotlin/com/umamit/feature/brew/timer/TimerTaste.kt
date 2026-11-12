package com.umamit.feature.brew.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.StepTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.ui.InfusionTasteEditor
import com.umamit.feature.brew.R
import kotlinx.coroutines.launch

/** Test tag of the timer's „Geschmack · N. Aufguss" button (SCR-05 v0.6.1, D-114). */
const val TIMER_TASTE_BUTTON_TAG = "timer_taste_button"

/** Test tag of the button's slot, there (its space kept) also while the button is hidden. */
const val TIMER_TASTE_SLOT_TAG = "timer_taste_slot"

/** Test tag of the timer's taste sheet. */
const val TIMER_TASTE_SHEET_TAG = "timer_taste_sheet"

/** Test tag of the taste sheet's *Fertig*. */
const val TIMER_TASTE_DONE_TAG = "timer_taste_done"

private val TasteIconSize = 18.dp

/**
 * The infusion the timer screen offers the taste of (JRN-03, D-114): the step's `position` (the step index), which
 * addresses the tasting writes, and the step table's [number] („2. Aufguss", counting *Aufguss* steps by position);
 * null for an *Aufschlagen* step, which the table does not number.
 */
@Immutable
data class TasteTarget(val position: Int, val number: Int?)

/**
 * The infusion ended last (D-114): the last ended (*Abgegossen*, *Aufguss jetzt beenden*) step that is not *Spülen*;
 * a skipped step never counts. Null for the quick timer and before the first infusion ended. From then on the
 * button's space is reserved in every phase, so nothing moves when the alert starts (rule 8, D-096).
 */
fun TimerUiState.Active.tasteTarget(): TasteTarget? {
    if (isQuick) return null
    var number = 0
    var target: TasteTarget? = null
    steps.forEachIndexed { index, step ->
        val counted = if (step.kind == StepKind.INFUSION) ++number else null
        if (step.status == StepStatus.DONE && step.kind != StepKind.RINSE) target = TasteTarget(index, counted)
    }
    return target
}

/**
 * Whether the button shows: in READY, RUNNING and PAUSED once an infusion has ended (SCR-05 v0.6.1). On the alert
 * surface and in FINISHED it is hidden with its space kept.
 */
fun TimerUiState.Active.tasteButtonShown(): Boolean = tasteTarget() != null && phase in TASTE_PHASES

private val TASTE_PHASES = setOf(TimerPhase.READY, TimerPhase.RUNNING, TimerPhase.PAUSED)

/**
 * What the timer screen needs for the taste of the infusion ended last (JRN-03, JRN-05, D-114): the taste and note
 * stored per step `position` of the shown session ([byPosition]; null until read) and the writes, each of the step at
 * the position it names. The writes go to `SessionTasting` beside the timer; none is an engine event.
 */
class TimerTasting(
    val byPosition: Map<Int, StepTasting>?,
    val onTaste: (position: Int, axis: TasteAxis, value: Int) -> Unit,
    val onReset: (position: Int) -> Unit,
    val onNote: (position: Int, note: String) -> Unit,
)

/** „2. Aufguss", or „Aufschlagen" for a step the step table does not number. */
@Composable
private fun TasteTarget.name(): String = number?.let { stringResource(R.string.brew_timer_taste_infusion, it) }
    ?: stringResource(R.string.brew_steps_kind_whisk)

/**
 * The button below the step strip (SCR-05 v0.6.1, D-114): „Geschmack · 1. Aufguss", „… ✓" once that infusion has a
 * value or a note. Composed only once an infusion has ended ([tasteTarget]); from then its space is kept in every
 * phase. Hidden on the alert surface and in FINISHED, decided in this composition so the alert's first frame never
 * shows it (rule 8). It fades in with `fade` when it appears (at once under reduce motion).
 */
@Composable
internal fun TasteButtonSlot(state: TimerUiState.Active, tasting: TimerTasting, onOpen: (TasteTarget) -> Unit) {
    val target = state.tasteTarget() ?: return
    val shown = state.tasteButtonShown()
    val name = target.name()
    val done = tasting.byPosition?.get(target.position)?.isEmpty == false
    val reduceMotion = UmamiTheme.reduceMotion
    val faded by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown && !reduceMotion) UmamiTheme.motion.fade() else snap(),
        label = "tasteButton",
    )
    Box(
        modifier = Modifier
            .padding(top = UmamiSpacing.XSmall)
            .testTag(TIMER_TASTE_SLOT_TAG),
        contentAlignment = Alignment.Center,
    ) {
        // The label with „✓", invisible: the slot is as tall as it from the start, so nothing moves when it comes.
        TasteButton(
            label = stringResource(R.string.brew_timer_taste_button_done, name),
            onClick = {},
            enabled = false,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
        )
        val description = stringResource(
            if (done) R.string.brew_timer_taste_description_done else R.string.brew_timer_taste_description,
            name,
        )
        TasteButton(
            label = stringResource(
                if (done) R.string.brew_timer_taste_button_done else R.string.brew_timer_taste_button,
                name,
            ),
            onClick = { onOpen(target) },
            enabled = shown,
            modifier = Modifier
                // Hidden at once, never faded out: the alert's first frame has no button (rule 8).
                .graphicsLayer { alpha = if (shown) faded else 0f }
                .then(
                    if (shown) {
                        Modifier
                            .testTag(TIMER_TASTE_BUTTON_TAG)
                            .semantics { contentDescription = description }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                ),
        )
    }
}

@Composable
private fun TasteButton(label: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(horizontal = UmamiSpacing.SideMargin),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(painterResource(UmamiIcons.Tune), contentDescription = null, modifier = Modifier.size(TasteIconSize))
        Spacer(Modifier.size(UmamiSpacing.Small))
        Text(text = label, textAlign = TextAlign.Center)
    }
}

/**
 * The taste sheet of the infusion the button named (SCR-05 v0.6.1, D-114), while that button shows. It is not
 * composed on the alert surface: an alert of any slot (the route then shows the ringing timer) removes it, and the
 * keyboard with it, in the alert's first frame, decided in this composition (rule 8); a write already started
 * completes in the view model, and the editor hands a pending note over as it leaves. The pre-alert leaves it open.
 * A user close (drag, scrim, back, *Fertig*) clears the overlay; one left open after its infusion stopped being the
 * one offered (another step ended) is cleared too.
 */
@Composable
internal fun TimerTasteHost(state: TimerUiState.Active, tasting: TimerTasting?, overlays: TimerOverlays) {
    val open = overlays.tasteFor ?: return
    val target = state.tasteTarget()
    val visible = tasting != null && target?.position == open && state.tasteButtonShown()
    if (!visible) {
        // Only off the alert: an alert closes it through closeAllNotOpenedIn, and it never reopens over it.
        LaunchedEffect(open, state.alerting) { if (!state.alerting) overlays.tasteFor = null }
        return
    }
    checkNotNull(tasting)
    checkNotNull(target)
    TimerTasteSheet(
        title = stringResource(R.string.brew_timer_taste_button, target.name()),
        position = target.position,
        stored = tasting.byPosition?.let { it[target.position] ?: StepTasting.EMPTY },
        onTaste = { axis, value -> tasting.onTaste(target.position, axis, value) },
        onReset = { tasting.onReset(target.position) },
        onNote = { tasting.onNote(target.position, it) },
        onDismissRequest = { overlays.tasteFor = null },
    )
}

/**
 * Material 3's modal sheet on the Steeping surface: the title with *Fertig* pinned at its end (D-110), below it the
 * shared [InfusionTasteEditor] scrolling (no radar). The keyboard does not open by itself. [stored] is null until the
 * session's taste is read: the editor then waits for it, so its note field starts from the stored note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerTasteSheet(
    title: String,
    position: Int,
    stored: StepTasting?,
    onTaste: (TasteAxis, Int) -> Unit,
    onReset: () -> Unit,
    onNote: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberTasteSheetState(reduceMotion)
    val scope = rememberCoroutineScope()
    // Set by *Fertig*: the sheet is on its way out, so a second tap does nothing.
    var leaving by remember { mutableStateOf(false) }
    val done = {
        if (!leaving) {
            leaving = true
            scope.launch { if (!reduceMotion) sheetState.hide() }.invokeOnCompletion {
                if (reduceMotion || !sheetState.isVisible) onDismissRequest() else leaving = false
            }
        }
    }
    // The taste as the sliders last said it, until the stored taste shows exactly that: a stale read must not flick
    // a value back.
    var pending by remember { mutableStateOf<TasteProfile?>(null) }
    LaunchedEffect(stored?.taste, pending) { if (pending != null && pending == stored?.taste) pending = null }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.testTag(TIMER_TASTE_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // The sheet's own window holds the focus: its keyboard goes with it, in the frame the sheet leaves.
        val keyboard = LocalSoftwareKeyboardController.current
        DisposableEffect(keyboard) { onDispose { keyboard?.hide() } }
        TasteSheetTitle(title, onDone = done)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
        ) {
            if (stored != null) {
                val taste = pending ?: stored.taste
                key(position) {
                    InfusionTasteEditor(
                        taste = taste,
                        note = stored.note,
                        onTasteChange = { axis, value ->
                            pending = taste.with(axis, value)
                            onTaste(axis, value)
                        },
                        onNoteSave = onNote,
                        onReset = {
                            pending = TasteProfile.EMPTY
                            onReset()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TasteSheetTitle(title: String, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onDone, modifier = Modifier.testTag(TIMER_TASTE_DONE_TAG)) {
            Text(stringResource(R.string.brew_timer_taste_sheet_done))
        }
    }
}

/** Fully expanded, no half-open stop; under reduce motion it opens expanded, without the slide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberTasteSheetState(reduceMotion: Boolean): SheetState {
    val density = LocalDensity.current
    return remember(reduceMotion) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp
