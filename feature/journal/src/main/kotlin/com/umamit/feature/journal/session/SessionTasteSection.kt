package com.umamit.feature.journal.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import com.umamit.core.ui.InfusionTasteEditor
import com.umamit.core.ui.TasteRadar
import com.umamit.core.ui.TasteRadarShape
import com.umamit.core.ui.radarValues
import com.umamit.core.ui.tasteSummary
import com.umamit.feature.journal.R

const val SESSION_TASTE_ADD_TAG = "session_taste_add"
const val SESSION_TASTE_ADJUST_TAG = "session_taste_adjust"
const val SESSION_TASTE_ALL_TAG = "session_taste_all"
const val SESSION_TASTE_SUMMARY_TAG = "session_taste_summary"
const val SESSION_TASTE_NOTE_LINE_TAG = "session_taste_note_line"

/** Test tag of the chip of the infusion at step [position]. */
fun sessionTasteChipTag(position: Int): String = "session_taste_chip_$position"

/** The chip *Alle* as the selection; a step position is never negative. */
private const val ALL = -1

private val CHEVRON_SIZE = 20.dp
private val DOT_SIZE = 6.dp
private val CHIP_GAP = 8.dp
private const val CHIP_BORDER_ALPHA = 0.4f
private const val CHEVRON_FOLDED_DEGREES = 90f
private const val CHEVRON_UNFOLDED_DEGREES = -90f

/**
 * GESCHMACK per infusion (JRN-03, JRN-05, SCR-16 v0.6.1, D-114 Q4, Q6, Q7, Q18), below the step table.
 *
 * Without any value or note the quiet row „Geschmack hinzufügen"; a tap shows the chips with the 1st infusion selected
 * and unfolded. With values: the chips *Alle* · „1. Aufguss" · „2." … (a dot on chips with data), the 1st infusion
 * selected and folded whenever the page opens. Folded: the infusion's radar (three axes) or its summary line, its note
 * in one line and *Anpassen* ⌄; unfolded: the radar and the shared [InfusionTasteEditor]. *Alle* shows the averaged
 * radar or summary only. The fold state stays across chip changes. Motion (03 §3.6): the fold uses the `sheet` spring
 * and `fade`, the chevron turns with it; a chip change cross-fades (`fade`); with reduce motion the fade only.
 */
@Composable
internal fun TasteSection(page: SessionPage, actions: SessionPageActions, modifier: Modifier = Modifier) {
    val infusions = page.infusions
    if (infusions.isEmpty()) return
    // The taste as the sliders last said it, per infusion, kept until the repository shows exactly that: a stale
    // emission still carrying older values must not flick a value back.
    var pending by remember(page.sessionId) { mutableStateOf<Map<Int, TasteProfile>>(emptyMap()) }
    LaunchedEffect(infusions, pending) {
        val stored = infusions.associate { it.position to it.taste }
        val open = pending.filter { (position, taste) -> stored[position] != taste }
        if (open != pending) pending = open
    }
    val shown = infusions.map { infusion -> pending[infusion.position]?.let { infusion.copy(taste = it) } ?: infusion }
    var opened by rememberSaveable(page.sessionId) { mutableStateOf(false) }
    var unfolded by rememberSaveable(page.sessionId) { mutableStateOf(false) }
    var selectedState by rememberSaveable(page.sessionId) { mutableIntStateOf(infusions.first().position) }
    val selected = selectedState.takeIf { it == ALL || shown.any { infusion -> infusion.position == it } }
        ?: shown.first().position
    val withChips = opened || shown.any { it.hasData }
    val motion = UmamiTheme.motion
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.session_taste),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        AnimatedVisibility(
            visible = !withChips,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            AddRow(
                onClick = {
                    opened = true
                    unfolded = true
                    selectedState = shown.first().position
                },
            )
        }
        AnimatedVisibility(
            visible = withChips,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfusionChips(shown, selected, onSelect = { selectedState = it })
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()))
                            .using(SizeTransform(clip = false) { _, _ -> motion.sheet() })
                    },
                    label = "taste chip",
                ) { target ->
                    val infusion = shown.firstOrNull { it.position == target }
                    if (infusion == null) {
                        AllTaste(TasteProfile.averages(shown.map { it.taste }))
                    } else {
                        key(infusion.position) {
                            InfusionTaste(
                                infusion = infusion,
                                unfolded = unfolded,
                                onToggle = { unfolded = !unfolded },
                                onSet = { axis, value ->
                                    opened = true
                                    pending = pending + (infusion.position to infusion.taste.with(axis, value))
                                    actions.setTaste(infusion.position, axis, value)
                                },
                                onNoteSave = { actions.setInfusionNote(infusion.position, it) },
                                onReset = {
                                    opened = true
                                    pending = pending + (infusion.position to TasteProfile.EMPTY)
                                    actions.clearTaste(infusion.position)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** „Geschmack hinzufügen ›": GESCHMACK without any value. */
@Composable
private fun AddRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .padding(horizontal = UmamiSpacing.SideMargin)
            .testTag(SESSION_TASTE_ADD_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.session_taste_add),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

/** The chip row, scrolling sideways: *Alle*, then one chip per infusion, numbered as the step table. */
@Composable
private fun InfusionChips(infusions: List<SessionInfusion>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        TasteChip(
            label = stringResource(R.string.session_taste_all),
            description = stringResource(R.string.session_taste_all),
            selected = selected == ALL,
            hasData = false,
            onClick = { onSelect(ALL) },
            modifier = Modifier.testTag(SESSION_TASTE_ALL_TAG),
        )
        val firstNumbered = infusions.firstOrNull { it.number != null }?.position
        infusions.forEach { infusion ->
            val full = infusion.fullName()
            TasteChip(
                label = if (infusion.number == null || infusion.position == firstNumbered) {
                    full
                } else {
                    stringResource(R.string.session_taste_infusion_short, infusion.number)
                },
                description = full,
                selected = selected == infusion.position,
                hasData = infusion.hasData,
                onClick = { onSelect(infusion.position) },
                modifier = Modifier.testTag(sessionTasteChipTag(infusion.position)),
            )
        }
    }
}

/** „2. Aufguss", or „Aufschlagen" for a step the table does not number. */
@Composable
private fun SessionInfusion.fullName(): String = when {
    number != null -> stringResource(R.string.session_taste_infusion, number)
    kind == StepKind.WHISK -> stringResource(R.string.session_step_whisk)
    else -> stringResource(R.string.session_taste_infusion, 1)
}

/** One `FilterChip`; TalkBack reads „1. Aufguss, ausgewählt, Geschmack eingetragen" (SCR-16 v0.6.1). */
@Composable
@Suppress("LongParameterList") // a chip's label, its spoken name, its two states, its tap and its tag
private fun TasteChip(
    label: String,
    description: String,
    selected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = stringResource(
        if (selected) R.string.session_taste_chip_selected else R.string.session_taste_chip_not_selected,
    )
    val state = if (hasData) {
        stringResource(R.string.session_taste_chip_state, selection, stringResource(R.string.session_taste_chip_data))
    } else {
        selection
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = state
        },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                if (hasData) {
                    Box(
                        Modifier
                            .size(DOT_SIZE)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
    )
}

/** *Alle*: the radar averaged per axis over the session's infusions, or its summary line; no *Anpassen*. */
@Composable
private fun AllTaste(averages: Map<TasteAxis, Double>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin)) {
        if (TasteRadarShape.isDrawn(averages)) {
            TasteRadar(averages)
        } else {
            SummaryLine(averages)
        }
    }
}

/** One infusion: folded its radar or summary line, its note in one line and *Anpassen*; unfolded the editor. */
@Composable
@Suppress("LongParameterList") // the infusion, the fold and the editor's three answers
private fun InfusionTaste(
    infusion: SessionInfusion,
    unfolded: Boolean,
    onToggle: () -> Unit,
    onSet: (TasteAxis, Int) -> Unit,
    onNoteSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    val motion = UmamiTheme.motion
    val values = infusion.taste.radarValues()
    val drawn = TasteRadarShape.isDrawn(values)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin)) {
        // The radar eases in with the third axis instead of pushing the sliders down in one frame (03 §3.6).
        AnimatedVisibility(
            visible = drawn,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            TasteRadar(values)
        }
        AnimatedVisibility(
            visible = !unfolded,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            Column {
                if (!drawn) SummaryLine(values)
                infusion.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = UmamiSpacing.XSmall).testTag(SESSION_TASTE_NOTE_LINE_TAG),
                    )
                }
            }
        }
        AdjustRow(unfolded = unfolded, onToggle = onToggle)
        AnimatedVisibility(
            visible = unfolded,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            InfusionTasteEditor(
                taste = infusion.taste,
                note = infusion.note,
                onTasteChange = onSet,
                onNoteSave = onNoteSave,
                onReset = onReset,
            )
        }
    }
}

/** „Umami 4 · Süße 3", or „Noch nichts eingetragen". */
@Composable
private fun SummaryLine(values: Map<TasteAxis, Double>) {
    Text(
        text = tasteSummary(values),
        style = MaterialTheme.typography.bodyLarge,
        color = if (values.isEmpty()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(top = UmamiSpacing.Small).testTag(SESSION_TASTE_SUMMARY_TAG),
    )
}

/** *Anpassen* with a chevron that turns with the fold; TalkBack „Geschmack anpassen, eingeklappt". */
@Composable
private fun AdjustRow(unfolded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(
        targetValue = if (unfolded) CHEVRON_UNFOLDED_DEGREES else CHEVRON_FOLDED_DEGREES,
        animationSpec = UmamiTheme.motion.sheet(),
        label = "taste chevron",
    )
    val description = stringResource(R.string.session_taste_adjust_description)
    val state = stringResource(if (unfolded) R.string.session_taste_unfolded else R.string.session_taste_folded)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .testTag(SESSION_TASTE_ADJUST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.session_taste_adjust),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        Icon(
            painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(CHEVRON_SIZE).rotate(turn),
        )
    }
}
