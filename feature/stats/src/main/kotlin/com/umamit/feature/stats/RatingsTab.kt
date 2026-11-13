package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.BestRecipeTea
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.TeaRatingRow
import com.umamit.core.model.StatsRange
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.feature.stats.charts.RatingLineChart
import com.umamit.feature.stats.charts.RatingPoint
import java.io.File

const val RATINGS_NO_RATING_TAG = "ratings_no_rating"
const val RATINGS_BEST_RECIPE_TAG = "ratings_best_recipe"

private const val LIST_LIMIT = 5

/**
 * *Bewertungen* (INS-02): how many sessions are rated, the rating over the range's periods, the rated teas and the
 * best recipe per tea (the full tab is rebuilt in #271). [figures] are null until the first
 * read and change in place with their range; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a
 * deleted tea. [thumbnail] loads a tea's cover photo (tests pass a placeholder).
 */
@Composable
fun RatingsTab(
    figures: TabFigures<RatingsStats>?,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
) {
    StatsTabColumn(scrollState, modifier) {
        if (figures == null) return@StatsTabColumn
        val stats = figures.stats
        val range = figures.range
        val noSessions = stats.sessionCount == 0
        val unrated = stats.ratedCount == 0
        AverageSection(stats, noSessions)
        val history = stringResource(R.string.stats_ratings_history)
        StatsSection(key = "ratings.history", title = history, isEmpty = noSessions) {
            if (unrated) NoRating() else HistoryChart(stats, range, history)
        }
        StatsSection(
            key = "ratings.top",
            title = stringResource(R.string.stats_ratings_top),
            isEmpty = noSessions || (stats.teaRanking.isEmpty() && !unrated),
        ) {
            if (unrated) NoRating() else TopRatedRows(stats.teaRanking.take(LIST_LIMIT), onOpenTea, thumbnail)
        }
        if (stats.bestRecipes.isNotEmpty()) {
            StatsSection(key = "ratings.best_recipe", title = stringResource(R.string.stats_ratings_best_recipe)) {
                BestRecipeRows(stats.bestRecipes.take(LIST_LIMIT), onOpenTea, thumbnail)
            }
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
    StatsSection(key = "ratings.average", title = stringResource(R.string.stats_tab_ratings), isEmpty = noSessions) {
        if (stats.ratedCount == 0) {
            NoRating()
            return@StatsSection
        }
        Column(Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
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
        }
    }
}

@Composable
private fun HistoryChart(stats: RatingsStats, range: StatsRange, title: String) {
    val resources = LocalResources.current
    val points = remember(stats.ratingPerPeriod) {
        stats.ratingPerPeriod.map {
            val label = it.period.label(resources)
            RatingPoint(
                label = label,
                rating = it.value,
                fullLabel = it.period.fullLabel(label),
            )
        }
    }
    RatingLineChart(
        entries = points,
        title = title,
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        scrollable = range == StatsRange.ALL || range == StatsRange.CUSTOM,
        selectionResetKey = range,
    )
}

/** A tea row: the cover photo or a leaf, name, meta and a chevron unless the tea is deleted. */
@Composable
private fun TeaRow(
    name: String,
    meta: String,
    teaId: String?,
    coverPhoto: File?,
    styleKey: String?,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
    modifier: Modifier = Modifier,
) {
    TeaListItem(
        title = name,
        meta = meta,
        modifier = modifier,
        onClick = teaId?.let { { onOpenTea(it) } },
        onClickLabel = stringResource(R.string.stats_open_tea),
        thumbnail = coverPhoto?.let { thumbnail(it) },
        placeholderColor = UmamiTheme.extendedColors.teaTypes.forStyle(styleKey),
    )
}

@Composable
private fun TopRatedRows(
    rows: List<TeaRatingRow>,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
    rows.forEach { row ->
        val count = pluralStringResource(R.plurals.stats_ratings_count, row.ratingCount, row.ratingCount)
        TeaRow(
            name = row.teaName,
            meta = stringResource(R.string.stats_ratings_top_meta, ChartNumbers.rating(row.average), count),
            teaId = row.teaId,
            coverPhoto = row.coverPhoto,
            styleKey = row.styleKey,
            onOpenTea = onOpenTea,
            thumbnail = thumbnail,
        )
    }
}

@Composable
private fun BestRecipeRows(
    teas: List<BestRecipeTea>,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
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
            coverPhoto = tea.coverPhoto,
            styleKey = tea.styleKey,
            onOpenTea = onOpenTea,
            thumbnail = thumbnail,
            modifier = Modifier.testTag(RATINGS_BEST_RECIPE_TAG),
        )
    }
}
