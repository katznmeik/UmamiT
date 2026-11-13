package com.umamit.feature.stats

import android.content.res.Resources
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.domain.stats.BestRecipeTea
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.StatsPeriod
import com.umamit.core.domain.stats.TeaRatingRow
import com.umamit.core.model.StatsRange
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.ui.DescriptorChip
import com.umamit.core.ui.TasteRadar
import com.umamit.core.ui.TasteRadarShape
import com.umamit.core.ui.labelRes
import com.umamit.core.ui.tasteSummary
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.feature.stats.charts.DistributionBars
import com.umamit.feature.stats.charts.RatingLineChart
import com.umamit.feature.stats.charts.RatingPoint
import java.time.format.TextStyle
import java.util.Locale

const val RATINGS_AVERAGE_TAG = "ratings_average"
const val RATINGS_NO_RATING_TAG = "ratings_no_rating"
const val RATINGS_TASTE_SUMMARY_TAG = "ratings_taste_summary"
const val RATINGS_BEST_RECIPE_TAG = "ratings_best_recipe"
const val RATINGS_DESCRIPTORS_TAG = "ratings_descriptors"

private const val LIST_LIMIT = 5
private const val STAR_LEVELS = 5
private val TeaRowMinHeight = UmamiSpacing.XXLarge

/**
 * *Bewertungen* (INS-02, INS-04): the average and its distribution, the rating over the range's periods, the top
 * rated teas, the best recipe per tea, the taste radar and the top descriptors. [stats] is null until the first read
 * and changes in place with [range]; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a
 * deleted tea.
 */
@Composable
fun RatingsTab(
    stats: RatingsStats?,
    range: StatsRange,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsTabColumn(scrollState, modifier) {
        if (stats == null) return@StatsTabColumn
        val noSessions = stats.sessionCount == 0
        val unrated = stats.ratedCount == 0
        AverageSection(stats, noSessions)
        val history = stringResource(R.string.stats_ratings_history)
        StatsSection(title = history, isEmpty = noSessions) {
            if (unrated) NoRating() else HistoryChart(stats, range, history)
        }
        StatsSection(
            title = stringResource(R.string.stats_ratings_top),
            isEmpty = noSessions || (stats.topRated.isEmpty() && !unrated),
        ) {
            if (unrated) NoRating() else TopRatedRows(stats.topRated.take(LIST_LIMIT), onOpenTea)
        }
        if (stats.bestRecipes.isNotEmpty()) {
            StatsSection(title = stringResource(R.string.stats_ratings_best_recipe)) {
                BestRecipeRows(stats.bestRecipes.take(LIST_LIMIT), onOpenTea)
            }
        }
        TasteSection(stats, noSessions)
        StatsSection(
            title = stringResource(R.string.stats_ratings_descriptors),
            isEmpty = stats.topDescriptors.isEmpty(),
        ) {
            DescriptorChips(stats)
        }
    }
}

@Composable
private fun NoRating() {
    Text(
        text = stringResource(R.string.stats_ratings_none),
        modifier = Modifier
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)
            .testTag(RATINGS_NO_RATING_TAG),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AverageSection(stats: RatingsStats, noSessions: Boolean) {
    StatsSection(title = stringResource(R.string.stats_tab_ratings), isEmpty = noSessions) {
        val average = stats.average
        if (average == null) {
            NoRating()
            return@StatsSection
        }
        Column(Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
            Text(
                text = stringResource(R.string.stats_ratings_average, ChartNumbers.rating(average)),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(RATINGS_AVERAGE_TAG),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.stats_ratings_rated_of,
                    stats.sessionCount,
                    stats.ratedCount,
                    stats.sessionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DistributionBars(
                countsByStars = List(STAR_LEVELS) { index ->
                    stats.distribution.firstOrNull { it.stars == index + 1 }?.count ?: 0
                },
                title = stringResource(R.string.stats_ratings_distribution),
                modifier = Modifier.padding(top = UmamiSpacing.Medium),
            )
        }
    }
}

@Composable
private fun HistoryChart(stats: RatingsStats, range: StatsRange, title: String) {
    val resources = LocalResources.current
    val points = remember(stats.ratingPerPeriod) {
        stats.ratingPerPeriod.map {
            RatingPoint(
                label = periodLabel(resources, it.period, full = false),
                rating = it.value,
                fullLabel = periodLabel(resources, it.period, full = true),
            )
        }
    }
    RatingLineChart(
        entries = points,
        title = title,
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        scrollable = range == StatsRange.ALL,
        selectionResetKey = range,
    )
}

/** „KW 44" / „Okt."; [full] reads „KW 44" / „Oktober 2026" for the value label and TalkBack. */
private fun periodLabel(resources: Resources, period: StatsPeriod, full: Boolean): String = when (period.unit) {
    PeriodUnit.WEEK -> resources.getString(R.string.stats_period_week, period.isoWeek ?: 0)

    PeriodUnit.MONTH -> if (full) {
        "${period.month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${period.month.year}"
    } else {
        period.month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
    }
}

@Composable
private fun TeaRow(
    name: String,
    meta: String,
    teaId: String?,
    onOpenTea: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.stats_ratings_open_tea)
    val click = if (teaId == null) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = openLabel, role = Role.Button) { onOpenTea(teaId) }
    }
    Column(modifier) {
        TeaRowContent(name, meta, showChevron = teaId != null, modifier = click)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            thickness = UmamiSpacing.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun TeaRowContent(name: String, meta: String, showChevron: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TeaRowMinHeight)
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showChevron) {
            Icon(
                painter = painterResource(UmamiIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopRatedRows(rows: List<TeaRatingRow>, onOpenTea: (String) -> Unit) {
    rows.forEach { row ->
        val count = pluralStringResource(R.plurals.stats_ratings_count, row.ratingCount, row.ratingCount)
        TeaRow(
            name = row.teaName,
            meta = stringResource(R.string.stats_ratings_top_meta, ChartNumbers.rating(row.average), count),
            teaId = row.teaId,
            onOpenTea = onOpenTea,
        )
    }
}

@Composable
private fun BestRecipeRows(teas: List<BestRecipeTea>, onOpenTea: (String) -> Unit) {
    val unnamed = stringResource(R.string.stats_ratings_recipe_unnamed)
    val separator = stringResource(R.string.stats_ratings_recipe_separator)
    val resources = LocalResources.current
    teas.forEach { tea ->
        val meta = tea.recipes.joinToString(separator) {
            resources.getString(
                R.string.stats_ratings_recipe_item,
                it.profileName ?: unnamed,
                ChartNumbers.rating(it.average),
            )
        }
        TeaRow(
            name = tea.teaName,
            meta = meta,
            teaId = tea.teaId,
            onOpenTea = onOpenTea,
            modifier = Modifier.testTag(RATINGS_BEST_RECIPE_TAG),
        )
    }
}

@Composable
private fun TasteSection(stats: RatingsStats, noSessions: Boolean) {
    val taste = stats.taste
    StatsSection(
        title = stringResource(R.string.stats_ratings_taste),
        isEmpty = noSessions || taste.infusionCount == 0 || taste.averages.isEmpty(),
    ) {
        Column(
            Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            if (TasteRadarShape.isDrawn(taste.averages)) {
                TasteRadar(taste.averages, Modifier.fillMaxWidth())
            } else {
                Text(
                    text = tasteSummary(taste.averages),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(RATINGS_TASTE_SUMMARY_TAG),
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.stats_ratings_taste_infusions,
                    taste.infusionCount,
                    taste.infusionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DescriptorChips(stats: RatingsStats) {
    val resources = LocalResources.current
    FlowRow(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin).testTag(RATINGS_DESCRIPTORS_TAG),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        stats.topDescriptors.take(LIST_LIMIT).forEach { descriptor ->
            val label = TasteDescriptor.fromKey(descriptor.key)?.let { resources.getString(it.labelRes()) }
            if (label != null) DescriptorChip(label = label, count = descriptor.count)
        }
    }
}
