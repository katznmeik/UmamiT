package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.DayPart
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.TeaSessions
import com.umamit.core.model.StatsRange
import com.umamit.core.ui.VolumeFormat
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.feature.stats.charts.ChartUnit
import com.umamit.feature.stats.charts.DonutSlice
import com.umamit.feature.stats.charts.PeriodBarChart
import com.umamit.feature.stats.charts.PeriodValue
import com.umamit.feature.stats.charts.SortenDonut
import java.io.File
import java.util.Locale

/**
 * *Überblick* (INS-01, INS-03, INS-06): the big numerals, sessions per period, the streak, the most brewed teas,
 * the Sorten and Tees donuts and the habits. [figures] are null until the first read and change in place with their
 * range; [onOpenTea] opens a collection tea's detail (SCR-09), never called for a deleted tea. [thumbnail] loads a
 * tea's cover photo (tests pass a placeholder).
 */
@Composable
fun OverviewTab(
    figures: TabFigures<OverviewStats>?,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
) {
    StatsTabColumn(scrollState, modifier) {
        if (figures == null) return@StatsTabColumn
        val stats = figures.stats
        val range = figures.range
        val none = stats.sessionCount == 0
        Numerals(stats)
        StatsSection(
            key = "overview.sessions",
            title = stringResource(R.string.stats_overview_section_sessions),
            summary = ChartNumbers.axis(stats.sessionCount.toDouble()),
            isEmpty = none,
        ) {
            SessionsChart(stats, range, figures.rangeKey)
        }
        StatsSection(
            key = "overview.streak",
            title = stringResource(R.string.stats_overview_section_streak),
            summary = days(stats.streak.current).takeIf { stats.streak.current > 0 },
        ) { StreakLine(stats) }
        StatsSection(
            key = "overview.most_brewed",
            title = stringResource(R.string.stats_overview_section_most_brewed),
            summary = stats.mostBrewed.firstOrNull()?.teaName,
            isEmpty = none,
        ) {
            stats.mostBrewed.forEachIndexed { index, tea ->
                MostBrewedRow(tea, showDivider = index < stats.mostBrewed.lastIndex, onOpenTea, thumbnail)
            }
        }
        StatsSection(
            key = "overview.sorten",
            title = stringResource(R.string.stats_overview_sorten),
            summary = stats.sorten.firstOrNull()?.sorte,
            isEmpty = none,
        ) {
            Sorten(stats, figures.rangeKey)
        }
        StatsSection(
            key = "overview.tees",
            title = stringResource(R.string.stats_overview_tees),
            summary = stats.teaDonut.firstOrNull { !it.isOther }?.teaName,
            isEmpty = none,
        ) {
            Tees(stats, figures.rangeKey)
        }
        val dayPartNames = dayPartNames()
        StatsSection(
            key = "overview.habits",
            title = stringResource(R.string.stats_overview_section_habits),
            summary = stats.dayParts.maxByOrNull { it.percent }?.takeIf { it.percent > 0 }
                ?.let { dayPartNames.getValue(it.key) },
            isEmpty = none,
        ) {
            Habits(stats, dayPartNames)
        }
    }
}

/** „42 Sitzungen · 12,4 l Tee · 24 Tees (+3 im Zeitraum)" in ONE row (R10), in the display font. */
@Composable
private fun Numerals(stats: OverviewStats) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
    ) {
        Numeral(
            ChartNumbers.axis(stats.sessionCount.toDouble()),
            pluralStringResource(R.plurals.stats_overview_sessions_label, stats.sessionCount),
            Modifier.weight(1f),
        )
        val (volume, volumeUnit) = VolumeFormat.parts(stats.volumeMl)
        Numeral(
            volume,
            stringResource(R.string.stats_overview_volume_label),
            Modifier.weight(1f),
            unit = volumeUnit,
        )
        val teas = pluralStringResource(R.plurals.stats_overview_teas_label, stats.teaCount)
        val added = stats.teasAddedInRange ?: 0
        Numeral(
            ChartNumbers.axis(stats.teaCount.toDouble()),
            if (added > 0) stringResource(R.string.stats_overview_teas_added, teas, added) else teas,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Numeral(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null) {
    Column(modifier) {
        // The unit in the body font on the same baseline: the display font's "l" reads as a bar (D-115 A2 V8).
        val unitStyle = MaterialTheme.typography.bodyLarge.toSpanStyle()
        Text(
            text = buildAnnotatedString {
                append(value)
                if (unit != null) withStyle(unitStyle) { append(" $unit") }
            },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionsChart(stats: OverviewStats, range: StatsRange, resetKey: Any) {
    val resources = LocalResources.current
    val entries = remember(stats.sessionsPerPeriod, resources) {
        stats.sessionsPerPeriod.map {
            val label = it.period.label(resources)
            PeriodValue(label, it.value.toDouble(), it.period.fullLabel(label))
        }
    }
    val monthly = stats.sessionsPerPeriod.firstOrNull()?.period?.unit == PeriodUnit.MONTH
    PeriodBarChart(
        entries = entries,
        unit = ChartUnit.SESSIONS,
        title = stringResource(
            if (monthly) R.string.stats_overview_sessions_per_month else R.string.stats_overview_sessions_per_week,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        scrollable = range == StatsRange.ALL || range == StatsRange.CUSTOM,
        selectionResetKey = resetKey,
    )
}

@Composable
private fun StreakLine(stats: OverviewStats) {
    val streak = stats.streak
    val text = if (streak.current > 0) {
        stringResource(
            R.string.stats_overview_streak,
            days(streak.current),
            days(streak.longest),
        )
    } else {
        if (streak.longest > 0) {
            stringResource(R.string.stats_overview_streak_none_longest, days(streak.longest))
        } else {
            stringResource(R.string.stats_overview_streak_none)
        }
    }
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun days(count: Int): String =
    pluralStringResource(R.plurals.stats_overview_days, count, ChartNumbers.axis(count.toDouble()))

@Composable
private fun MostBrewedRow(
    tea: TeaSessions,
    showDivider: Boolean,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
    val teaId = tea.teaId
    TeaListItem(
        title = tea.teaName,
        meta = stringResource(R.string.stats_overview_times, tea.sessions),
        onClick = teaId?.let { { onOpenTea(it) } },
        onClickLabel = stringResource(R.string.stats_open_tea),
        thumbnail = tea.coverPhoto?.let { thumbnail(it) },
        placeholderColor = UmamiTheme.extendedColors.teaTypes.forStyle(tea.styleKey),
        showDivider = showDivider,
    )
}

@Composable
private fun Sorten(stats: OverviewStats, resetKey: Any) {
    val types = UmamiTheme.extendedColors.teaTypes
    val other = stringResource(R.string.stats_overview_other)
    val slices = stats.sorten.map { share ->
        val sorte = share.sorte
        val color = sorte?.let { types.forStyle(it.lowercase(Locale.ROOT).replace(' ', '_')) }
            ?.takeIf { it != types.other }
        DonutSlice(sorte ?: other, share.sessions, share.percent, color, isOther = sorte == null)
    }
    SortenDonut(
        slices = slices,
        title = stringResource(R.string.stats_overview_sorten),
        modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        selectionResetKey = resetKey,
    )
}

/** The donut „Tees" (R6): the share of sessions per tea, the five largest named, the rest „Andere". */
@Composable
private fun Tees(stats: OverviewStats, resetKey: Any) {
    val other = stringResource(R.string.stats_overview_other)
    val slices = stats.teaDonut.map { share ->
        DonutSlice(
            label = if (share.isOther) other else share.teaName,
            count = share.sessions,
            percent = share.percent,
            isOther = share.isOther,
            key = if (share.isOther) "other" else share.teaId?.let { "tea:$it" } ?: "name:${share.teaName}",
        )
    }
    SortenDonut(
        slices = slices,
        title = stringResource(R.string.stats_overview_tees),
        modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        selectionResetKey = resetKey,
    )
}
