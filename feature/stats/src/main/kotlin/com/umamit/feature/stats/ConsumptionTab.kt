package com.umamit.feature.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
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
import coil3.compose.rememberAsyncImagePainter
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.collection.CupAverage
import com.umamit.core.domain.stats.ConsumptionStats
import com.umamit.core.domain.stats.PeriodUnit
import com.umamit.core.domain.stats.PeriodValue
import com.umamit.core.domain.stats.RunningOutTea
import com.umamit.core.model.StatsRange
import com.umamit.core.model.StockLevel
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.stats.charts.ChartUnit
import com.umamit.feature.stats.charts.PeriodBarChart
import java.io.File
import kotlin.math.roundToLong
import com.umamit.feature.stats.charts.PeriodValue as BarValue

private const val ONE_DECIMAL_BELOW_GRAMS = 10.0
private const val CENTS = 100.0

/** Grams for a numeral or a row: one decimal only below 10 g („6,5 g", „38 g"), German format (INS-05). */
internal fun consumptionGrams(grams: Double): String = if (grams < ONE_DECIMAL_BELOW_GRAMS) {
    GermanFormat.gramsOneDecimal(grams)
} else {
    GermanFormat.gramsTrimmed(grams.roundToLong().toDouble())
}

/** How a bar chart of the tab is titled, by the period unit. */
private class BarTitles(val week: Int, val month: Int)

private val GRAMS_TITLES = BarTitles(
    R.string.stats_consumption_grams_per_week,
    R.string.stats_consumption_grams_per_month,
)
private val CUPS_TITLES = BarTitles(R.string.stats_consumption_cups_per_week, R.string.stats_consumption_cups_per_month)
private val MONEY_TITLES = BarTitles(
    R.string.stats_consumption_money_per_week,
    R.string.stats_consumption_money_per_month,
)

/**
 * *Verbrauch* (INS-05): grams, cups, money spent, the average cost per cup and the teas running out. [figures]
 * are null until the first read and change in place with their range; [onOpenTea] opens a collection tea's
 * detail (SCR-09),
 * never called for a deleted tea. [thumbnail] loads a tea's cover photo (tests pass a placeholder).
 */
@Composable
fun ConsumptionTab(
    figures: TabFigures<ConsumptionStats>?,
    scrollState: ScrollState,
    onOpenTea: (teaId: String) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (File) -> Painter? = { rememberAsyncImagePainter(it) },
) {
    StatsTabColumn(scrollState, modifier) {
        if (figures == null) return@StatsTabColumn
        val stats = figures.stats
        val none = stats.grams <= 0.0 && stats.cups == 0
        val monthly = stats.gramsPerPeriod.firstOrNull()?.period?.unit == PeriodUnit.MONTH
        val bars = BarSetup(monthly, figures.range)
        StatsSection(title = stringResource(R.string.stats_consumption_section_grams), isEmpty = none) {
            Numeral(
                value = stringResource(R.string.stats_chart_grams, consumptionGrams(stats.grams)),
                label = stringResource(R.string.stats_consumption_in_range),
            )
            Bars(stats.gramsPerPeriod, ChartUnit.GRAMS, GRAMS_TITLES, bars) { it }
        }
        StatsSection(title = stringResource(R.string.stats_consumption_section_cups), isEmpty = none) {
            Bars(stats.cupsPerPeriod, ChartUnit.CUPS, CUPS_TITLES, bars) { it.toDouble() }
        }
        StatsSection(title = stringResource(R.string.stats_consumption_section_money)) {
            Money(stats, bars)
        }
        stats.costPerCup?.let { average ->
            StatsSection(title = stringResource(R.string.stats_consumption_section_cost)) { CostLine(average) }
        }
        if (stats.runningOut.isNotEmpty()) {
            StatsSection(title = stringResource(R.string.stats_consumption_section_running_out)) {
                stats.runningOut.forEachIndexed { index, tea ->
                    RunningOutRow(tea, showDivider = index < stats.runningOut.lastIndex, onOpenTea, thumbnail)
                }
            }
        }
    }
}

private class BarSetup(val monthly: Boolean, val range: StatsRange)

@Composable
private fun Numeral(value: String, label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)) {
        Text(
            text = value,
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
private fun <T> Bars(
    values: List<PeriodValue<T>>,
    unit: ChartUnit,
    titles: BarTitles,
    setup: BarSetup,
    toDouble: (T) -> Double,
) {
    val resources = LocalResources.current
    val entries = remember(values, resources) {
        values.map {
            val label = it.period.label(resources)
            BarValue(label, toDouble(it.value), it.period.fullLabel(label))
        }
    }
    PeriodBarChart(
        entries = entries,
        unit = unit,
        title = stringResource(if (setup.monthly) titles.month else titles.week),
        modifier = Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.SideMargin),
        scrollable = setup.range == StatsRange.ALL,
        selectionResetKey = setup.range,
    )
}

@Composable
private fun Money(stats: ConsumptionStats, setup: BarSetup) {
    if (stats.hasPurchases) {
        MoneySpent(stats, setup)
    } else {
        Text(
            text = stringResource(R.string.stats_consumption_no_purchases),
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoneySpent(stats: ConsumptionStats, setup: BarSetup) {
    Column {
        Numeral(
            value = stringResource(R.string.stats_chart_euros, GermanFormat.euros(stats.moneyCents)),
            label = stringResource(R.string.stats_consumption_in_range),
        )
        Bars(stats.moneyPerPeriod, ChartUnit.EUROS, MONEY_TITLES, setup) { it / CENTS }
    }
}

@Composable
private fun CostLine(average: CupAverage) {
    Text(
        text = stringResource(
            R.string.stats_consumption_cost_per_cup,
            GermanFormat.euros(average.cupCents),
            pluralStringResource(R.plurals.stats_consumption_sessions, average.sessionCount, average.sessionCount),
        ),
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun RunningOutRow(
    tea: RunningOutTea,
    showDivider: Boolean,
    onOpenTea: (String) -> Unit,
    thumbnail: @Composable (File) -> Painter?,
) {
    TeaListItem(
        title = tea.teaName,
        meta = if (tea.level == StockLevel.EMPTY) {
            stringResource(R.string.stats_consumption_empty)
        } else {
            stringResource(R.string.stats_consumption_low, consumptionGrams(tea.remainingGrams))
        },
        onClick = { onOpenTea(tea.teaId) },
        onClickLabel = stringResource(R.string.stats_open_tea),
        thumbnail = tea.coverPhoto?.let { thumbnail(it) },
        placeholderColor = UmamiTheme.extendedColors.teaTypes.forStyle(tea.styleKey),
        showDivider = showDivider,
    )
}
