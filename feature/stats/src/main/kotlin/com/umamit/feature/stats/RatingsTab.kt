package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.BestRecipeTea
import com.umamit.core.domain.stats.NewTea
import com.umamit.core.domain.stats.RatingsStats
import com.umamit.core.domain.stats.TeaRatingRow
import com.umamit.core.model.StatsRange
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.feature.stats.charts.RatingLineChart
import com.umamit.feature.stats.charts.RatingPoint
import java.io.File

const val RATINGS_NO_RATING_TAG = "ratings_no_rating"
const val RATINGS_BEST_RECIPE_TAG = "ratings_best_recipe"
const val RATINGS_RANKING_ROW_TAG = "ratings_ranking_row"
const val RATINGS_NEW_ROW_TAG = "ratings_new_row"
const val RATINGS_RANKING_MORE_TAG = "ratings_ranking_more"
const val RATINGS_NEW_MORE_TAG = "ratings_new_more"

private const val RANKING_LIMIT = 10
private const val NEW_LIMIT = 5
private const val LIST_LIMIT = 5

/**
 * *Bewertungen* (INS-02, D-115 A1 R4): the Tee-Rangliste (top 10, „Alle anzeigen"), Neu entdeckt (5, hidden under
 * *Gesamt*), the rating over the range's periods and the best recipe per tea; „In diesem Zeitraum nichts bewertet"
 * above Neu entdeckt when nothing was rated. [figures] are null until the first read and change in place with
 * their range; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a deleted tea. [thumbnail]
 * loads a tea's cover photo (tests pass a placeholder).
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
        if (unrated && !noSessions) {
            NoRating()
        } else {
            val top = stats.teaRanking.firstOrNull()
            StatsSection(
                key = "ratings.ranking",
                title = stringResource(R.string.stats_ratings_ranking),
                summary = top?.let {
                    stringResource(R.string.stats_chart_stars_value, ChartNumbers.rating(it.average))
                },
                isEmpty = noSessions,
            ) {
                RankingRows(stats.teaRanking, range, onOpenTea, thumbnail)
            }
        }
        if (range != StatsRange.ALL) {
            StatsSection(
                key = "ratings.new",
                title = stringResource(R.string.stats_ratings_new),
                summary = stats.newlyDiscovered.size.toString(),
                isEmpty = stats.newlyDiscovered.isEmpty(),
            ) {
                NewRows(stats.newlyDiscovered, range, onOpenTea, thumbnail)
            }
        }
        if (!unrated || noSessions) {
            val history = stringResource(R.string.stats_ratings_history)
            val latest = stats.ratingPerPeriod.lastOrNull { it.value != null }?.value
            StatsSection(
                key = "ratings.history",
                title = history,
                summary = latest?.let { stringResource(R.string.stats_chart_stars_value, ChartNumbers.rating(it)) },
                isEmpty = noSessions,
            ) {
                HistoryChart(stats, range, history)
            }
        }
        if (stats.bestRecipes.isNotEmpty()) {
            StatsSection(
                key = "ratings.best_recipe",
                title = stringResource(R.string.stats_ratings_best_recipe),
                summary = stats.bestRecipes.first().teaName,
            ) {
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
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium)
            .testTag(RATINGS_NO_RATING_TAG),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** „Alle anzeigen (N)": expands the list above it in place. */
@Composable
private fun ShowAll(count: Int, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = UmamiSpacing.Small).testTag(tag),
    ) {
        Text(stringResource(R.string.stats_ratings_show_all, count))
    }
}

@Composable
private fun RankingRows(
    rows: List<TeaRatingRow>,
    range: StatsRange,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
    var expanded by rememberSaveable(range) { mutableStateOf(false) }
    val shown = if (expanded) rows else rows.take(RANKING_LIMIT)
    shown.forEach { row ->
        val meta = stringResource(R.string.stats_ratings_rank_meta, ChartNumbers.rating(row.average), row.ratingCount)
        TeaRow(
            name = row.teaName,
            meta = meta,
            teaId = row.teaId,
            coverPhoto = row.coverPhoto,
            styleKey = row.styleKey,
            onOpenTea = onOpenTea,
            thumbnail = thumbnail,
            modifier = Modifier.testTag(RATINGS_RANKING_ROW_TAG),
        )
    }
    if (!expanded && rows.size > RANKING_LIMIT) {
        ShowAll(rows.size, RATINGS_RANKING_MORE_TAG) { expanded = true }
    }
}

@Composable
private fun NewRows(
    rows: List<NewTea>,
    range: StatsRange,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
    var expanded by rememberSaveable(range) { mutableStateOf(false) }
    val shown = if (expanded) rows else rows.take(NEW_LIMIT)
    shown.forEach { row ->
        TeaRow(
            name = row.teaName,
            meta = row.average?.let { stringResource(R.string.stats_ratings_average, ChartNumbers.rating(it)) }
                ?: stringResource(R.string.stats_ratings_unrated),
            teaId = row.teaId,
            coverPhoto = row.coverPhoto,
            styleKey = row.styleKey,
            onOpenTea = onOpenTea,
            thumbnail = thumbnail,
            modifier = Modifier.testTag(RATINGS_NEW_ROW_TAG),
        )
    }
    if (!expanded && rows.size > NEW_LIMIT) {
        ShowAll(rows.size, RATINGS_NEW_MORE_TAG) { expanded = true }
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
