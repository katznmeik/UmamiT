package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Temperatur · Ziehzeit · Aufgüsse with line icons (03 §3.8). A missing value
 * shows „–". Each column is one TalkBack item with the value in words.
 *
 * @param temperatureCelsius already a multiple of 5 (GEN-12).
 * @param steepSeconds the first infusion's steep time.
 */
@Composable
fun StatTriplet(temperatureCelsius: Int?, steepSeconds: Int?, infusions: Int?, modifier: Modifier = Modifier) {
    val unknown = stringResource(R.string.ds_stat_unknown)
    val temperatureLabel = stringResource(R.string.ds_stat_temperature)
    val steepLabel = stringResource(R.string.ds_stat_steep_time)
    val infusionsLabel = stringResource(R.string.ds_stat_infusions)
    StatTriplet(
        first = StatFigure(
            icon = painterResource(UmamiIcons.Thermometer),
            value = temperatureCelsius?.let { stringResource(R.string.ds_stat_temperature_value, it) } ?: unknown,
            label = temperatureLabel,
            description = temperatureCelsius
                ?.let { pluralStringResource(R.plurals.ds_stat_temperature_description, it, it) }
                ?: stringResource(R.string.ds_stat_unknown_description, temperatureLabel),
        ),
        second = StatFigure(
            icon = painterResource(UmamiIcons.Schedule),
            value = steepSeconds?.let(::clockText) ?: unknown,
            label = steepLabel,
            description = steepSeconds
                ?.let { stringResource(R.string.ds_stat_steep_time_description, spokenDuration(it)) }
                ?: stringResource(R.string.ds_stat_unknown_description, steepLabel),
        ),
        third = StatFigure(
            icon = painterResource(UmamiIcons.Infusions),
            value = infusions?.toString() ?: unknown,
            label = infusionsLabel,
            description = infusions
                ?.let { pluralStringResource(R.plurals.ds_stat_infusions_description, it, it) }
                ?: stringResource(R.string.ds_stat_unknown_description, infusionsLabel),
        ),
        modifier = modifier,
    )
}

/**
 * One figure of a [StatTriplet]: its line [icon], the [value] („12"), the [label] below it („Sitzungen") and what
 * TalkBack reads for the whole column ([description]).
 */
class StatFigure(val icon: Painter, val value: String, val label: String, val description: String)

/**
 * Three figures side by side with hairlines between them (03 §3.8): the layout of Temperatur · Ziehzeit ·
 * Aufgüsse, for other figures too (the tea detail's sessions, last brew and stock; SCR-09, D-100). Each column
 * is one TalkBack item read as its [StatFigure.description].
 */
@Composable
fun StatTriplet(first: StatFigure, second: StatFigure, third: StatFigure, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatColumn(first, Modifier.weight(1f))
        StatDivider()
        StatColumn(second, Modifier.weight(1f))
        StatDivider()
        StatColumn(third, Modifier.weight(1f))
    }
}

@Composable
private fun StatColumn(figure: StatFigure, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = UmamiSpacing.XXSmall, vertical = UmamiSpacing.XSmall)
            .semantics(mergeDescendants = true) { contentDescription = figure.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
    ) {
        Icon(
            painter = figure.icon,
            contentDescription = null,
            modifier = Modifier.size(UmamiSpacing.Large),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = figure.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = figure.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = UmamiSpacing.Small),
        thickness = UmamiSpacing.Hairline,
        color = MaterialTheme.colorScheme.outline,
    )
}
