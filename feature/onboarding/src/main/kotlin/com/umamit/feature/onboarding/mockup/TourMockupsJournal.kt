package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.PrimaryActionCard
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.component.TeaListItem
import com.umamit.core.designsystem.component.TimerRing
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.TimerTheme
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.RatingStars
import com.umamit.feature.onboarding.BandImage
import com.umamit.feature.onboarding.HOME_HEADER_RATIO
import com.umamit.feature.onboarding.R
import com.umamit.feature.onboarding.TimerBandImage
import com.umamit.core.designsystem.R as DsR

/** M-SESSION: a session page of Morgentau, the two star rows and *Geschmack hinzufügen* marked. */
@Composable
internal fun SessionMockup() {
    val tea = MockupDemo.morgentau
    MockupScreen {
        Box(
            Modifier.fillMaxWidth().height(HERO_HEIGHT).background(tea.color().copy(alpha = HERO_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UmamiIcons.Leaf), null, Modifier.size(HERO_LEAF), tint = tea.color())
        }
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) {
            MockupGap(height = UmamiSpacing.XSmall)
            Text(
                stringResource(tea.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MockupGap(height = UmamiSpacing.XXSmall)
            DetailRow(stringResource(R.string.mockup_session_vessel), MockupDemo.vessel())
            DetailRow(stringResource(R.string.mockup_session_leaf), MockupDemo.leaf())
            DetailRow(stringResource(R.string.mockup_session_water), MockupDemo.water())
        }
        SectionHeader(stringResource(R.string.mockup_session_rating))
        Column(Modifier.padding(horizontal = UmamiSpacing.XSmall).mockupMarker()) {
            RatingStars(
                value = TEA_RATING,
                onChange = {},
                label = stringResource(R.string.mockup_journal_rating_tea),
                labelWidth = RATING_LABEL_WIDTH,
            )
            RatingStars(
                value = BREWING_RATING,
                onChange = {},
                label = stringResource(R.string.mockup_journal_rating_brewing),
                labelWidth = RATING_LABEL_WIDTH,
            )
        }
        SectionHeader(stringResource(R.string.mockup_session_taste))
        Row(
            Modifier.padding(horizontal = UmamiSpacing.Medium).fillMaxWidth().mockupMarker(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.mockup_session_taste_add),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(painterResource(UmamiIcons.ChevronRight), null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** M-STATS: *Statistik* on *Verbrauch*, the *Getrunken* section with the cost per litre marked. */
@Composable
internal fun StatsMockup() {
    MockupScreen {
        Row(Modifier.fillMaxWidth().padding(start = UmamiSpacing.Medium, top = UmamiSpacing.Small)) {
            Text(
                stringResource(R.string.mockup_stats_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MockupIcon(UmamiIcons.MoreVert)
        }
        MockupGap()
        Box(
            Modifier
                .padding(horizontal = UmamiSpacing.Medium)
                .fillMaxWidth()
                .height(SEGMENT_HEIGHT)
                .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium)) {
                repeat(PERIOD_COUNT) { MockupBar(PERIOD_BAR) }
            }
        }
        MockupGap()
        StatsTabs()
        MockupGap(height = UmamiSpacing.XSmall)
        SectionHeader(stringResource(R.string.mockup_stats_consumption_section_drunk))
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) {
            MockupBar(BIG_VALUE_WIDTH, height = BIG_VALUE_HEIGHT)
            MockupGap(height = UmamiSpacing.XSmall)
            MockupBar(VALUE_CAPTION_WIDTH)
            MockupGap()
            Chart()
            MockupGap(height = UmamiSpacing.XSmall)
            Text(
                stringResource(R.string.mockup_stats_consumption_cost_per_liter, MockupDemo.costPerLiter()),
                modifier = Modifier.mockupMarker(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatsTabs() {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val tabs = listOf(
        R.string.mockup_stats_tab_overview,
        R.string.mockup_stats_tab_ratings,
        R.string.mockup_stats_tab_consumption,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == tabs.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(tab),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    MockupGap(height = UmamiSpacing.XSmall)
                    Box(
                        Modifier
                            .width(TAB_INDICATOR_WIDTH)
                            .height(TAB_INDICATOR_HEIGHT)
                            .background(if (selected) primary else background),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** A plain bar chart: the last bar (the current week) in the primary colour, as the Statistik draws it. */
@Composable
private fun Chart() {
    Row(
        Modifier.fillMaxWidth().height(CHART_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(CHART_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        CHART_LEVELS.forEachIndexed { index, level ->
            val color = if (index == CHART_LEVELS.lastIndex) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(Modifier.weight(1f).fillMaxHeight(level).background(color))
        }
    }
}

/** M-DATA: *Daten & Sicherung*, *Sicherung erstellen* marked. */
@Composable
internal fun DataMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_backup_title), back = true)
        SectionHeader(stringResource(R.string.mockup_backup_section_backup))
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) {
            MockupBar(STATUS_BAR_WIDTH)
            MockupGap()
            PillButton(
                text = stringResource(R.string.mockup_backup_create),
                onClick = {},
                modifier = Modifier.mockupMarker(),
            )
            MockupGap()
            Box(
                Modifier
                    .width(OUTLINED_WIDTH)
                    .height(OUTLINED_HEIGHT)
                    .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { MockupBar(OUTLINED_BAR) }
            MockupGap(height = UmamiSpacing.Large)
            MockupBars(listOf(LINE_LONG, LINE_MEDIUM, LINE_SHORT))
            MockupGap(height = UmamiSpacing.Large)
            MockupBars(listOf(LINE_SHORT, LINE_LONG, LINE_MEDIUM))
        }
    }
}

private const val TEA_RATING = 4.0
private const val BREWING_RATING = 4.5
private const val HERO_ALPHA = 0.25f
private const val PERIOD_COUNT = 5
private val CHART_LEVELS = listOf(0.7f, 0.3f, 0.6f, 0.2f, 0.5f, 0.9f, 0.4f, 0.75f, 0.35f, 0.15f)
private val HERO_HEIGHT = 72.dp
private val HERO_LEAF = 36.dp
private val RATING_LABEL_WIDTH = 64.dp
private val PERIOD_BAR = 20.dp
private val BIG_VALUE_WIDTH = 90.dp
private val BIG_VALUE_HEIGHT = 24.dp
private val VALUE_CAPTION_WIDTH = 70.dp
private val TAB_INDICATOR_WIDTH = 32.dp
private val TAB_INDICATOR_HEIGHT = 3.dp
private val CHART_HEIGHT = 60.dp
private val CHART_GAP = 6.dp
private val STATUS_BAR_WIDTH = 110.dp
private val OUTLINED_WIDTH = 140.dp
private val OUTLINED_HEIGHT = 40.dp
private val OUTLINED_BAR = 90.dp
private val LINE_LONG = 190.dp
private val LINE_MEDIUM = 150.dp
private val LINE_SHORT = 100.dp
