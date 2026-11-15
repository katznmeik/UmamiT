package com.umamit.feature.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.BREWING_RATING_STARS_TAG
import com.umamit.core.ui.RATING_STARS_TAG
import com.umamit.core.ui.RatingStars

/** Five 48 dp star targets. */
private val STARS_WIDTH = 240.dp

/**
 * The two labelled star rows of a session (JRN-02, D-117 Q4): „Tee" above „Zubereitung", both always shown, nothing
 * marks an unrated one (Q12). Each row saves on its own through its callback; [tea] and [brewing] are shown as given.
 * With [ownNode] each row is its own merged node, so a parent's merge (the sheet's) does not swallow the stars'
 * TalkBack adjust action.
 */
@Composable
internal fun RatingRows(
    tea: Double?,
    brewing: Double?,
    onTea: (Double?) -> Unit,
    onBrewing: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    ownNode: Boolean = false,
) {
    val teaLabel = stringResource(R.string.journal_rating_tea)
    val brewingLabel = stringResource(R.string.journal_rating_brewing)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyMedium
    // One layout decision for both rows: the widest label sets the shared column, and when that column plus the five
    // stars does not fit, both labels go above their stars (D-117, so the stars start at the same x in both rows).
    val labelColumn = with(density) {
        maxOf(measurer.measure(teaLabel, style).size.width, measurer.measure(brewingLabel, style).size.width).toDp()
    } + UmamiSpacing.Small
    BoxWithConstraints(modifier = modifier) {
        val above = labelColumn + STARS_WIDTH > maxWidth
        RowsColumn(tea, brewing, onTea, onBrewing, ownNode, labelColumn, above, teaLabel, brewingLabel)
    }
}

@Composable
private fun RowsColumn(
    tea: Double?,
    brewing: Double?,
    onTea: (Double?) -> Unit,
    onBrewing: (Double?) -> Unit,
    ownNode: Boolean,
    labelColumn: Dp,
    above: Boolean,
    teaLabel: String,
    brewingLabel: String,
) {
    Column {
        RowNode(ownNode) {
            RatingStars(
                value = tea,
                onChange = onTea,
                label = teaLabel,
                labelWidth = labelColumn,
                labelAbove = above,
                spokenLabel = stringResource(R.string.journal_rating_tea_spoken),
                removeLabel = stringResource(R.string.journal_rating_tea_remove),
                rowTag = RATING_STARS_TAG,
            )
        }
        RowNode(ownNode) {
            RatingStars(
                value = brewing,
                onChange = onBrewing,
                label = brewingLabel,
                labelWidth = labelColumn,
                labelAbove = above,
                spokenLabel = stringResource(R.string.journal_rating_brewing),
                removeLabel = stringResource(R.string.journal_rating_brewing_remove),
                rowTag = BREWING_RATING_STARS_TAG,
            )
        }
    }
}

@Composable
private fun RowNode(ownNode: Boolean, content: @Composable () -> Unit) {
    Box(modifier = if (ownNode) Modifier.semantics(mergeDescendants = true) {} else Modifier) { content() }
}
