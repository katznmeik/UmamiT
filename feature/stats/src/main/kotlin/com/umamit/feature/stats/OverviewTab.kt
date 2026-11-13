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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.stats.DayPart
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.TeaSessions
import com.umamit.core.model.StatsRange
import com.umamit.feature.stats.charts.ChartNumbers
import com.umamit.feature.stats.charts.ChartUnit
import com.umamit.feature.stats.charts.DonutSlice
import com.umamit.feature.stats.charts.HabitBars
import com.umamit.feature.stats.charts.HabitShare
import com.umamit.feature.stats.charts.PeriodBarChart
import com.umamit.feature.stats.charts.PeriodValue
import com.umamit.feature.stats.charts.SortenDonut
import java.io.File
import java.util.Locale

private const val PERCENT = 100.0

/**
 * *Überblick* (INS-01, INS-03, INS-06): the big numerals, sessions per period, the streak, the most brewed teas,
 * the Sorten donut and the habits. [stats] is null until the first read and changes in place with [range];
 * [onOpenTea] opens a collection tea's detail (SCR-09), never called for a deleted tea. [thumbnail] loads a tea's
 * cover photo (tests pass a placeholder).
 */
@Composable
fun OverviewTab(
    stats: OverviewStats?,
    range: StatsRange,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
) {
    StatsTabColumn(scrollState, modifier) {
        if (stats == null) return@StatsTabColumn
        val none = stats.sessionCount == 0
        Numerals(stats)
        StatsSection(title = stringResource(R.string.stats_overview_section_sessions), isEmpty = none) {
            SessionsChart(stats, range)
        }
        StatsSection(title = stringResource(R.string.stats_overview_section_streak)) { StreakLine(stats) }
        StatsSection(title = stringResource(R.string.stats_overview_section_most_brewed), isEmpty = none) {
            stats.mostBrewed.forEachIndexed { index, tea ->
                MostBrewedRow(tea, showDivider = index < stats.mostBrewed.lastIndex, onOpenTea, thumbnail)
            }
        }
        StatsSection(title = stringResource(R.string.stats_overview_section_sorten), isEmpty = none) {
            Sorten(stats, range)
        }
        StatsSection(title = stringResource(R.string.stats_overview_section_habits), isEmpty = none) {
            Habits(stats)
        }
    }
}

/** „42 Sitzungen · 96 Tassen" and „24 Tees · +3 im Zeitraum" (the plus hidden at 0), in the display font. */
@Composable
private fun Numerals(stats: OverviewStats) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium)) {
            Numeral(
                stats.sessionCount,
                pluralStringResource(R.plurals.stats_overview_sessions_label, stats.sessionCount),
                Modifier.weight(1f),
            )
            Numeral(
                stats.cupCount,
                pluralStringResource(R.plurals.stats_overview_cups_label, stats.cupCount),
                Modifier.weight(1f),
            )
        }
        val teas = pluralStringResource(R.plurals.stats_overview_teas_label, stats.teaCount)
        Numeral(
            stats.teaCount,
            if (stats.teasAddedInRange > 0) {
                stringResource(R.string.stats_overview_teas_added, teas, stats.teasAddedInRange)
            } else {
                teas
            },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Numeral(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = ChartNumbers.axis(value.toDouble()),
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
private fun SessionsChart(stats: OverviewStats, range: StatsRange) {
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
        scrollable = range == StatsRange.ALL,
        selectionResetKey = range,
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
        stringResource(R.string.stats_overview_streak_none)
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
        onClickLabel = stringResource(R.string.stats_overview_open_tea),
        thumbnail = tea.coverPhoto?.let { thumbnail(it) },
        placeholderColor = UmamiTheme.extendedColors.teaTypes.forStyle(tea.styleKey),
        showDivider = showDivider,
    )
}

@Composable
private fun Sorten(stats: OverviewStats, range: StatsRange) {
    val types = UmamiTheme.extendedColors.teaTypes
    val other = stringResource(R.string.stats_overview_other)
    val slices = stats.sorten.map { share ->
        val sorte = share.sorte
        val color = sorte?.let { types.forStyle(it.lowercase(Locale.ROOT).replace(' ', '_')) }
            ?.takeIf { it != types.other }
        DonutSlice(sorte ?: other, share.sessions, color, isOther = sorte == null)
    }
    SortenDonut(
        slices = slices,
        title = stringResource(R.string.stats_overview_sorten_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        selectionResetKey = range,
    )
}

@Composable
private fun Habits(stats: OverviewStats) {
    val dayPartTitle = stringResource(R.string.stats_overview_day_part)
    val weekdayTitle = stringResource(R.string.stats_overview_weekday)
    val dayPartNames = mapOf(
        DayPart.MORGEN to stringResource(R.string.stats_overview_morning),
        DayPart.MITTAG to stringResource(R.string.stats_overview_noon),
        DayPart.NACHMITTAG to stringResource(R.string.stats_overview_afternoon),
        DayPart.ABEND to stringResource(R.string.stats_overview_evening),
    )
    val weekdayNames = stringArrayResource(R.array.stats_overview_weekdays_short)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
    ) {
        HabitRow(
            dayPartTitle,
            stats.dayParts.map { HabitShare(dayPartNames.getValue(it.key), it.percent / PERCENT) },
        )
        HabitRow(
            weekdayTitle,
            stats.weekdays.map { HabitShare(weekdayNames[it.key.value - 1], it.percent / PERCENT) },
        )
    }
}

@Composable
private fun HabitRow(title: String, entries: List<HabitShare>) {
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HabitBars(entries = entries, title = title, modifier = Modifier.fillMaxWidth())
    }
}
