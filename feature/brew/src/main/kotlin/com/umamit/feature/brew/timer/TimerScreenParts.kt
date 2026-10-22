package com.umamit.feature.brew.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.KettleHint
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ReliabilityBanner
import com.umamit.core.designsystem.component.TimerRing
import com.umamit.core.designsystem.component.TimerRingColors
import com.umamit.core.designsystem.component.TimerRingDefaults
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R

private val PhotoHeight = 280.dp
private val PhotoOverlap = 150.dp
private val TimerPillMinHeight = 72.dp
private val TimerPillMinWidth = 200.dp
private val OvertimeDotSize = 10.dp
private const val PAUSED_RING_ALPHA = 0.6f
private const val PHOTO_FADE_START = 0.35f
private const val PLACEHOLDER_TEMPERATURE = 100
private const val PLACEHOLDER_STEP = 10
private const val PLACEHOLDER_SECONDS = 600

/** What the timer's body shows; one value, so the body can move between the two surfaces. */
internal class TimerBodyArgs(
    val state: TimerUiState.Active,
    val now: Long,
    val nowElapsedMs: () -> Long,
    val overlays: TimerOverlays,
    val actions: TimerActions,
    val reliabilityProblem: Boolean,
    val onFixReliability: () -> Unit,
    val teaPhoto: Painter?,
)

@Composable
internal fun TimerBody(args: TimerBodyArgs) {
    val state = args.state
    val now = args.now
    val overlays = args.overlays
    val actions = args.actions
    val teaPhoto = args.teaPhoto
    Box(modifier = Modifier.fillMaxSize()) {
        // No photo on the alert surface, but its space stays, so nothing moves when the alert starts.
        if (teaPhoto != null && !state.alerting) TeaPhoto(teaPhoto)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(bottom = UmamiSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimerTopBar(state, overlays, actions)
            if (teaPhoto != null) Spacer(Modifier.height(PhotoOverlap))
            TimerHeader(state)
            Spacer(Modifier.height(UmamiSpacing.Large))
            TimerRingArea(state, now, args.nowElapsedMs, actions)
            Spacer(Modifier.height(UmamiSpacing.Medium))
            // One fixed slot for the kettle hint and the overtime: the pill below never moves under the
            // finger, not at *Start*, not when the alert starts, not when the overtime turns large.
            Box(contentAlignment = Alignment.Center) {
                BelowRingSpace()
                TimerBelowRing(state, now)
            }
            Spacer(Modifier.height(UmamiSpacing.Large))
            TimerControls(state, actions)
            // Below the controls, so it never moves them; hidden during the alert.
            if (args.reliabilityProblem && !state.alerting) {
                ReliabilityBanner(
                    message = stringResource(R.string.brew_timer_reliability),
                    onFix = args.onFixReliability,
                    modifier = Modifier.padding(
                        top = UmamiSpacing.Large,
                        start = UmamiSpacing.SideMargin,
                        end = UmamiSpacing.SideMargin,
                    ),
                )
            }
        }
    }
}

/**
 * Invisible copies of the slot's two tallest contents, a two-line kettle hint
 * and the large overtime: the slot is as tall as they are at any font scale,
 * so the pill below never moves (D-058).
 */
@Composable
private fun BelowRingSpace() {
    Box(
        modifier = Modifier
            .alpha(0f)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        KettleHint(
            temperatureCelsius = PLACEHOLDER_TEMPERATURE,
            detail = stringResource(
                R.string.brew_timer_ready_detail,
                stringResource(R.string.brew_steps_kind_infusion),
                PLACEHOLDER_STEP,
                clockText(PLACEHOLDER_SECONDS),
            ),
        )
        Overtime(PLACEHOLDER_SECONDS)
    }
}

/** The tea photo at the top, fading into the background (SCR-05). Decorative: the title names the tea. */
@Composable
private fun TeaPhoto(photo: Painter) {
    val background = MaterialTheme.colorScheme.background
    Image(
        painter = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(PhotoHeight)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(PHOTO_FADE_START to Color.Transparent, 1f to background))
            },
    )
}

@Composable
private fun TimerTopBar(state: TimerUiState.Active, overlays: TimerOverlays, actions: TimerActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.XXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.back) {
            Icon(painterResource(UmamiIcons.ArrowBack), contentDescription = stringResource(R.string.brew_timer_back))
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = overlays::openMenu) {
                Icon(
                    painterResource(UmamiIcons.MoreVert),
                    contentDescription = stringResource(R.string.brew_timer_more),
                )
            }
            // Not composed once the alert hides it: Material's menu would otherwise keep its popup for the
            // exit animation over the first frames of the alert (rule 8). A normal close still animates.
            if (overlays.menuComposable) {
                DropdownMenu(expanded = overlays.menuShown, onDismissRequest = { overlays.menuOpen = false }) {
                    state.menuItems().forEach { item ->
                        DropdownMenuItem(text = { Text(stringResource(item.label)) }, onClick = { actions.menu(item) })
                    }
                }
            }
        }
    }
}

private val TimerMenuItem.label: Int
    get() = when (this) {
        TimerMenuItem.AdjustBrew -> R.string.brew_timer_menu_adjust
        TimerMenuItem.EndStepNow -> R.string.brew_timer_menu_end_step
        TimerMenuItem.SkipStep -> R.string.brew_timer_menu_skip
        TimerMenuItem.EditSteps -> R.string.brew_timer_menu_edit_steps
        TimerMenuItem.AddStep -> R.string.brew_timer_menu_add_step
        TimerMenuItem.FinishSession -> R.string.brew_timer_menu_finish
        TimerMenuItem.DiscardSession -> R.string.brew_timer_menu_discard
        TimerMenuItem.TimerSettings -> R.string.brew_timer_menu_settings
    }

/** Tea name, „55 °C · Aufguss 1 von 3" and the step dots (TMR-02). */
@Composable
private fun ColumnScope.TimerHeader(state: TimerUiState.Active) {
    Text(
        text = state.teaName ?: stringResource(R.string.brew_timer_quick_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = UmamiSpacing.SideMargin)
            .semantics { heading() },
    )
    val step = state.currentStep
    if (step != null) {
        val label = stepLabel(step.kind, state.stepIndex, state.steps.size)
        Text(
            text = step.tempC?.let { stringResource(R.string.brew_timer_meta, it, label) } ?: label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                top = UmamiSpacing.XXSmall,
                start = UmamiSpacing.SideMargin,
                end = UmamiSpacing.SideMargin,
            ),
        )
    }
    if (state.steps.size > 1) StepDots(state.steps, state.stepIndex)
}

/** One dot per step: done and current filled, pending outlined; a rinse is a small square (SCR-05). */
@Composable
private fun StepDots(steps: List<TimerUiStep>, current: Int) {
    val color = LocalContentColor.current
    FlowRow(
        modifier = Modifier
            .padding(top = UmamiSpacing.Small, start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin)
            // The step label above says the same in words.
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        steps.forEachIndexed { index, step ->
            val rinse = step.kind == StepKind.RINSE
            val shape = if (rinse) RectangleShape else CircleShape
            val dot = Modifier.size(if (rinse) 6.dp else 8.dp)
            Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = when {
                        index == current -> dot.background(color, shape)
                        step.status == StepStatus.DONE -> dot.background(color.copy(alpha = 0.6f), shape)
                        else -> dot.border(1.dp, color.copy(alpha = 0.6f), shape)
                    },
                )
            }
        }
    }
}

@Composable
private fun TimerRingArea(state: TimerUiState.Active, now: Long, nowElapsedMs: () -> Long, actions: TimerActions) {
    val step = state.currentStep
    val frameDriven = state.phase == TimerPhase.RUNNING && !state.longStep
    // Short running steps redraw every frame; the lambda reads the frame in the draw phase only.
    val frame = remember { mutableLongStateOf(0L) }
    if (frameDriven) {
        LaunchedEffect(state) {
            while (true) withInfiniteAnimationFrameMillis { frame.longValue = it }
        }
    }
    val coarse = state.progressAt(now)
    val progress: () -> Float = if (frameDriven) {
        remember(state, nowElapsedMs) {
            {
                frame.longValue
                state.progressAt(nowElapsedMs())
            }
        }
    } else {
        remember(coarse) { { coarse } }
    }
    // PAUSED dims only the ring, the liquor and the marker; digits and caption keep ≥ 4.5:1 (03 §6, D-058).
    // The body moves to the alert surface with this state: the alert snaps to full strength and never
    // inherits the paused dim, not even for a frame (rule 8, `alertEnter`).
    val animatedDim by animateFloatAsState(
        targetValue = if (state.phase == TimerPhase.PAUSED) PAUSED_RING_ALPHA else 1f,
        animationSpec = if (state.alerting) snap() else UmamiTheme.motion.fade(),
        label = "pausedRing",
    )
    val dim = if (state.alerting) 1f else animatedDim
    val ready = state.phase == TimerPhase.READY
    val startLabel = stringResource(R.string.brew_timer_ring_start)
    TimerRing(
        progress = progress,
        remainingSeconds = state.display(now).remainingSeconds.toInt(),
        caption = stringResource(state.caption(now)),
        liquorColor = UmamiTheme.extendedColors.liquor.green,
        stepDescription = step?.let { stepLabel(it.kind, state.stepIndex, state.steps.size) },
        preAlertPosition = state.preAlertPosition(),
        // One identity per step: a resume after the pre-alert must not replay the glow (D-046).
        preAlertGlowTick = (state.stepIndex + 1L).takeIf { state.preAlertPlayed },
        longStep = state.longStep,
        colors = TimerRingDefaults.colors().dimmed(dim),
        modifier = Modifier
            .testTag(TIMER_RING_TAG)
            .semantics { this[RingDimKey] = dim }
            .then(
                if (ready) {
                    Modifier.clickable(onClickLabel = startLabel) { actions.event(TimerEvent.Start) }
                } else {
                    Modifier
                },
            ),
    )
}

/** Test seam: the dim factor the ring is drawn with (1 = full strength). */
internal val RingDimKey = SemanticsPropertyKey<Float>("RingDim")

/** These colours with the ring, the liquor and the marker at [factor]; the digits and the caption unchanged. */
internal fun TimerRingColors.dimmed(factor: Float): TimerRingColors = if (factor >= 1f) {
    this
} else {
    copy(
        track = track.copy(alpha = track.alpha * factor),
        progress = progress.copy(alpha = progress.alpha * factor),
        marker = marker.copy(alpha = marker.alpha * factor),
        glow = glow.copy(alpha = glow.alpha * factor),
        liquorAlpha = liquorAlpha * factor,
    )
}

/** READY: the kettle hint and the next step; ALERTING: the overtime. */
@Composable
private fun TimerBelowRing(state: TimerUiState.Active, now: Long) {
    when (state.phase) {
        TimerPhase.READY -> {
            val step = state.currentStep ?: return
            val detail = stringResource(
                R.string.brew_timer_ready_detail,
                stringResource(step.kind.label),
                state.stepIndex + 1,
                clockText(step.plannedSeconds),
            )
            val kettle = state.kettleTempC
            if (kettle != null) {
                KettleHint(temperatureCelsius = kettle, detail = detail)
            } else {
                Text(detail, style = MaterialTheme.typography.bodyLarge)
            }
        }

        TimerPhase.ALERTING -> Overtime(state.display(now).overtimeSeconds.toInt())

        else -> Unit
    }
}

/** „+0:07"; from 15 s on „+2:30 überzogen", large, in ink with a warm-accent dot (TMR-06, D-055). */
@Composable
private fun Overtime(seconds: Int) {
    val description = stringResource(R.string.brew_timer_overtime_description, spokenDuration(seconds))
    // TalkBack reads „2 Minuten 30 Sekunden überzogen" instead of the digits.
    val semantics = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
    if (seconds < PROMINENT_OVERTIME_SECONDS) {
        Text(
            text = stringResource(R.string.brew_timer_overtime, clockText(seconds)),
            style = MaterialTheme.typography.titleLarge,
            modifier = semantics,
        )
    } else {
        Row(
            modifier = semantics.padding(horizontal = UmamiSpacing.SideMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(OvertimeDotSize)
                    .background(UmamiTheme.extendedColors.warmAccent, CircleShape),
            )
            Spacer(Modifier.size(UmamiSpacing.Small))
            Text(
                text = stringResource(R.string.brew_timer_overtime_prominent, clockText(seconds)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The one primary control per state (TMR-03), FINISHED with *Weiterer Aufguss* as the second. */
@Composable
private fun TimerControls(state: TimerUiState.Active, actions: TimerActions) {
    val pill = Modifier
        .heightIn(min = TimerPillMinHeight)
        .widthIn(min = TimerPillMinWidth)
    when (state.phase) {
        TimerPhase.READY -> PillButton(
            text = stringResource(R.string.brew_timer_start),
            onClick = { actions.event(TimerEvent.Start) },
            icon = painterResource(UmamiIcons.Play),
            modifier = pill,
        )

        TimerPhase.RUNNING -> PillButton(
            text = stringResource(R.string.brew_timer_pause),
            onClick = { actions.event(TimerEvent.Pause) },
            icon = painterResource(UmamiIcons.Pause),
            modifier = pill,
        )

        TimerPhase.PAUSED -> PillButton(
            text = stringResource(R.string.brew_timer_resume),
            onClick = { actions.event(TimerEvent.Resume) },
            icon = painterResource(UmamiIcons.Play),
            modifier = pill,
        )

        TimerPhase.ALERTING -> PillButton(
            text = stringResource(
                if (state.currentStep?.kind ==
                    StepKind.RINSE
                ) {
                    R.string.brew_timer_poured_rinse
                } else {
                    R.string.brew_timer_poured
                },
            ),
            onClick = { actions.event(TimerEvent.Poured) },
            modifier = pill,
        )

        TimerPhase.FINISHED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton(
                text = stringResource(R.string.brew_timer_finish),
                onClick = { actions.menu(TimerMenuItem.FinishSession) },
                modifier = pill,
            )
            TextButton(
                onClick = { actions.menu(TimerMenuItem.AddStep) },
                modifier = Modifier.padding(top = UmamiSpacing.XSmall),
            ) {
                Text(stringResource(R.string.brew_timer_add_step))
            }
        }
    }
}
