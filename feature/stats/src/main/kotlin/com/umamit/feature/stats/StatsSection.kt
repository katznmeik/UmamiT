package com.umamit.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.germanUppercase
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

private val BottomClearance = 72.dp

/** The chevron's turn from folded (pointing right) to open (pointing down). */
private const val OPEN_DEGREES = 90f

/**
 * The vertical scroll every tab's content is (SCR-18): [scrollState] belongs to the shell, so a tab keeps its place
 * across a swipe and a re-selection of the Statistik tab scrolls it to the top (D-049).
 */
@Composable
fun StatsTabColumn(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = BottomClearance),
        content = content,
    )
}

/** The folded sections of the three tabs (INS-08, D-115 A1, R8): the folded keys and how a section changes one. */
class StatsFolds(val folded: Set<String>, val onChange: (key: String, folded: Boolean) -> Unit)

/**
 * Where a [StatsSection] keeps its fold: the shell provides the persisted set (`statsFolded`); without one (a tab
 * shown alone) each section remembers its own fold while it is composed.
 */
@Suppress("CompositionLocalAllowlist") // the shell tells every section below it, without a parameter per section
val LocalStatsFolds = compositionLocalOf<StatsFolds?> { null }

/** The test tag of the fold header of the section [key]. */
fun statsSectionTag(key: String): String = "stats_section_$key"

/**
 * One section of a tab (SCR-18, INS-08): its overline [title] as a heading row with a turning chevron. A tap folds
 * the section to that one line, which then also reads [summary] (the key number: „SITZUNGEN · 42"; nothing when
 * null). [key] names the section in `statsFolded`. With [isEmpty] (nothing in the range) the body reads „In diesem
 * Zeitraum nichts" quietly instead (D-115 Q15). The body expands and collapses in ≈ 200 ms, at once under reduce
 * motion; TalkBack hears „eingeklappt" or „ausgeklappt" with the action.
 */
@Composable
@Suppress("LongParameterList") // the key, title, summary, empty flag and body of one section
fun StatsSection(
    key: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    isEmpty: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val folds = LocalStatsFolds.current
    var local by rememberSaveable(key) { mutableStateOf(false) }
    val folded = if (folds != null) key in folds.folded else local
    val toggle = {
        if (folds != null) folds.onChange(key, !folded) else local = !folded
    }
    val motion = UmamiTheme.motion
    Column(modifier = modifier.fillMaxWidth().padding(top = UmamiSpacing.Small)) {
        FoldHeader(key, title, if (folded) summary else null, folded, toggle)
        AnimatedVisibility(
            visible = !folded,
            enter = expandVertically(motion.exit()) + fadeIn(motion.exit()),
            exit = shrinkVertically(motion.exit()) + fadeOut(motion.exit()),
        ) {
            Column {
                if (isEmpty) {
                    Text(
                        text = stringResource(R.string.stats_section_empty),
                        modifier = Modifier.padding(
                            horizontal = UmamiSpacing.SideMargin,
                            vertical = UmamiSpacing.Small,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun FoldHeader(key: String, title: String, summary: String?, folded: Boolean, onToggle: () -> Unit) {
    val line = if (summary != null) stringResource(R.string.stats_section_folded_title, title, summary) else title
    val state = stringResource(if (folded) R.string.stats_section_folded else R.string.stats_section_unfolded)
    val action = stringResource(if (folded) R.string.stats_section_unfold else R.string.stats_section_fold)
    val turn by animateFloatAsState(
        targetValue = if (folded) 0f else OPEN_DEGREES,
        animationSpec = UmamiTheme.motion.exit(),
        label = "statsChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .testTag(statsSectionTag(key))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                contentDescription = line
                stateDescription = state
            }
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.germanUppercase(),
            style = UmamiTheme.textStyles.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = UmamiSpacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        Icon(
            painter = painterResource(UmamiIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
}
