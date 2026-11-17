package com.umamit.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteSet

/** Test tag of [TasteInfo]'s row (the button that folds it). */
const val TASTE_INFO_TAG = "taste_info"

/** Test tag of [TasteInfo]'s how-to, present while it is unfolded. */
const val TASTE_INFO_HOWTO_TAG = "taste_info_howto"

/** Test tag of the entry of [axis] in [TasteInfo], present while it is unfolded. */
fun tasteInfoEntryTag(axis: TasteAxis): String = "taste_info_entry_${axis.name.lowercase()}"

private val ICON_SIZE = 20.dp
private const val CHEVRON_FOLDED_DEGREES = 90f
private const val CHEVRON_UNFOLDED_DEGREES = -90f

/** What one slider of the info says (08 §4a): its [line] and what 0 and 5 mean. */
private class TasteInfoTexts(val line: Int, val zero: Int, val five: Int)

private fun TasteAxis.infoTexts(): TasteInfoTexts = when (this) {
    TasteAxis.UMAMI -> TasteInfoTexts(
        R.string.ui_taste_info_umami_line,
        R.string.ui_taste_info_umami_zero,
        R.string.ui_taste_info_umami_five,
    )

    TasteAxis.SWEETNESS -> TasteInfoTexts(
        R.string.ui_taste_info_sweetness_line,
        R.string.ui_taste_info_sweetness_zero,
        R.string.ui_taste_info_sweetness_five,
    )

    TasteAxis.BITTERNESS -> TasteInfoTexts(
        R.string.ui_taste_info_bitterness_line,
        R.string.ui_taste_info_bitterness_zero,
        R.string.ui_taste_info_bitterness_five,
    )

    TasteAxis.ASTRINGENCY -> TasteInfoTexts(
        R.string.ui_taste_info_astringency_line,
        R.string.ui_taste_info_astringency_zero,
        R.string.ui_taste_info_astringency_five,
    )

    TasteAxis.BODY -> TasteInfoTexts(
        R.string.ui_taste_info_body_line,
        R.string.ui_taste_info_body_zero,
        R.string.ui_taste_info_body_five,
    )

    TasteAxis.AROMA -> TasteInfoTexts(
        R.string.ui_taste_info_aroma_line,
        R.string.ui_taste_info_aroma_zero,
        R.string.ui_taste_info_aroma_five,
    )

    TasteAxis.ROAST -> TasteInfoTexts(
        R.string.ui_taste_info_roast_line,
        R.string.ui_taste_info_roast_zero,
        R.string.ui_taste_info_roast_five,
    )

    TasteAxis.ACIDITY -> TasteInfoTexts(
        R.string.ui_taste_info_acidity_line,
        R.string.ui_taste_info_acidity_zero,
        R.string.ui_taste_info_acidity_five,
    )

    TasteAxis.FRESHNESS -> TasteInfoTexts(
        R.string.ui_taste_info_freshness_line,
        R.string.ui_taste_info_freshness_zero,
        R.string.ui_taste_info_freshness_five,
    )

    TasteAxis.SPICE -> TasteInfoTexts(
        R.string.ui_taste_info_spice_line,
        R.string.ui_taste_info_spice_zero,
        R.string.ui_taste_info_spice_five,
    )
}

/**
 * „Was bedeuten die Regler?" (03 §3.8 `TasteInfo`, 08 §4a, JRN-03, D-128 Q17–Q18): a foldable row above the taste
 * sliders with the how-to and, per slider of [set] in the set's order, its name, one line and „0 = … · 5 = …". Its own
 * composable: each host places it (the session page inside *Anpassen*'s fold, the timer's sheet from v0.8.0), so it
 * is not part of [InfusionTasteEditor]. Folded whenever it enters the composition, which is when its host opens: the
 * fold is not saved. The fold uses the `sheet` spring for the height and `fade` (03 §3.6), the chevron turns with it;
 * under reduce motion the height does not animate. TalkBack reads the row as „Was bedeuten die Regler?, eingeklappt"
 * and an entry as „Umami. Herzhafte, brühige Fülle – wie Dashi oder Nori. 0 bedeutet keine, 5 bedeutet dicht wie
 * Brühe."
 */
@Composable
fun TasteInfo(modifier: Modifier = Modifier, set: TasteSet = TasteSet.GREEN) {
    var unfolded by remember { mutableStateOf(false) }
    val motion = UmamiTheme.motion
    val turn by animateFloatAsState(
        targetValue = if (unfolded) CHEVRON_UNFOLDED_DEGREES else CHEVRON_FOLDED_DEGREES,
        animationSpec = motion.sheet(),
        label = "taste info chevron",
    )
    val title = stringResource(R.string.ui_taste_info_title)
    val state = stringResource(if (unfolded) R.string.ui_taste_info_unfolded else R.string.ui_taste_info_folded)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { unfolded = !unfolded }
                .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                .semantics {
                    contentDescription = title
                    stateDescription = state
                }
                .testTag(TASTE_INFO_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            Icon(
                painterResource(R.drawable.ic_taste_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
            Icon(
                painterResource(UmamiIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = unfolded,
            enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = UmamiSpacing.Small)) {
                Text(
                    text = stringResource(R.string.ui_taste_info_howto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(TASTE_INFO_HOWTO_TAG),
                )
                set.axes.forEach { axis -> TasteInfoEntry(axis) }
            }
        }
    }
}

/** One entry: the slider's name, its line and „0 = … · 5 = …". */
@Composable
private fun TasteInfoEntry(axis: TasteAxis) {
    val texts = axis.infoTexts()
    val name = axis.tasteLabel()
    val line = stringResource(texts.line)
    val zero = stringResource(texts.zero)
    val five = stringResource(texts.five)
    val spoken = stringResource(R.string.ui_taste_info_spoken, name, line, zero, five)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UmamiSpacing.Medium)
            .testTag(tasteInfoEntryTag(axis))
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.ui_taste_info_scale, zero, five),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
