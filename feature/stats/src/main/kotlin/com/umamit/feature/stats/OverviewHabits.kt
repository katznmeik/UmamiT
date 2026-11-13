package com.umamit.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.domain.stats.DayPart
import com.umamit.core.domain.stats.OverviewStats
import com.umamit.feature.stats.charts.HabitBars
import com.umamit.feature.stats.charts.HabitShare

private const val PERCENT = 100.0

@Composable
internal fun dayPartNames(): Map<DayPart, String> = mapOf(
    DayPart.MORGEN to stringResource(R.string.stats_overview_morning),
    DayPart.MITTAG to stringResource(R.string.stats_overview_noon),
    DayPart.NACHMITTAG to stringResource(R.string.stats_overview_afternoon),
    DayPart.ABEND to stringResource(R.string.stats_overview_evening),
)

@Composable
internal fun Habits(stats: OverviewStats, dayPartNames: Map<DayPart, String>) {
    val dayPartTitle = stringResource(R.string.stats_overview_day_part)
    val weekdayTitle = stringResource(R.string.stats_overview_weekday)
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
