package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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

/** M-START: Start with the A3 band, *Aufguss starten* (marked), the quick timer and *Zuletzt aufgegossen*. */
@Composable
internal fun StartMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.onboarding_app_name), settings = true)
        BandImage(
            res = if (UmamiTheme.isDark) UmamiIcons.HomeHeaderDark else UmamiIcons.HomeHeaderLight,
            ratio = HOME_HEADER_RATIO,
        )
        MockupGap()
        PrimaryActionCard(
            title = stringResource(R.string.mockup_home_start_brew),
            subtitle = stringResource(R.string.mockup_home_start_brew_subtitle),
            onClick = {},
            modifier = Modifier.padding(horizontal = UmamiSpacing.Medium).mockupMarker(),
        )
        MockupGap()
        Row(
            Modifier.padding(horizontal = UmamiSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            Icon(painterResource(UmamiIcons.Timer), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(DsR.string.ds_choose_timer_quick),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MockupGap()
        SectionHeader(stringResource(R.string.mockup_home_recent))
        MockupDemo.teas.forEach { tea ->
            TeaListItem(
                title = stringResource(tea.name),
                meta = listOf(MockupDemo.steepTime(), MockupDemo.temperature()).joinToString(" · "),
                onClick = {},
                placeholderColor = tea.color(),
            )
        }
    }
}

/**
 * M-TWO: the timer on its own surface (follows *Design*, D-128): the A13 band, the seal, the other timer's row
 * (marked), the tea, the ring. Drawn here; nothing of `feature:brew` (D-137 item 24).
 */
@Composable
internal fun TwoTimersMockup() {
    TimerMockupScreen(otherTimerRow = true)
}

/**
 * The timer screen of M-TWO and M-TIMER-MENU on its own surface: the band, ← and ⋮, the seal, the tea and the ring;
 * [otherTimerRow] adds the other timer's row (marked), [overlay] draws on top (an open menu).
 */
@Composable
internal fun TimerMockupScreen(otherTimerRow: Boolean, overlay: @Composable BoxScope.() -> Unit = {}) {
    TimerTheme(reduceMotion = UmamiTheme.reduceMotion) {
        MockupScreen {
            Box(Modifier.fillMaxSize()) {
                TimerBandImage()
                Row(
                    Modifier.fillMaxWidth().padding(UmamiSpacing.XXSmall),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MockupIcon(UmamiIcons.ArrowBack)
                    MockupIcon(UmamiIcons.MoreVert)
                }
                TimerColumn(otherTimerRow)
                overlay()
            }
        }
    }
}

@Composable
private fun TimerColumn(otherTimerRow: Boolean) {
    val tea = MockupDemo.morgentau
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(SEAL_TOP))
        Box(
            Modifier
                .size(SEAL_SIZE)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UmamiIcons.Leaf), null, tint = tea.color())
        }
        MockupGap(height = UmamiSpacing.XSmall)
        if (otherTimerRow) OtherTimerRow()
        MockupGap(height = UmamiSpacing.XSmall)
        Text(
            stringResource(tea.name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.mockup_timer_temperature_step, MockupDemo.temperature(), 1, INFUSIONS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MockupGap()
        TimerRing(
            progress = { RING_PROGRESS },
            remainingSeconds = MockupDemo.STEEP_SECONDS,
            caption = stringResource(R.string.mockup_timer_text_running),
            liquorColor = UmamiTheme.extendedColors.liquor.green,
            reduceMotion = true,
        )
    }
}

/** The other timer's row under the band (marked): the quick timer's clock and *Pause*. */
@Composable
private fun OtherTimerRow() {
    Row(
        Modifier.mockupMarker().padding(horizontal = UmamiSpacing.XSmall, vertical = UmamiSpacing.XXSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        Icon(painterResource(UmamiIcons.Timer), null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
        Text(
            "${stringResource(DsR.string.ds_choose_timer_quick)}  ${MockupDemo.steepTime()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.mockup_timer_action_pause),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** M-MYTEAS-MENU: *Sammlung* › *Meine Tees* with the demo teas and ⋮ open, *Tee ohne Katalog hinzufügen* marked. */
@Composable
internal fun MyTeasMenuMockup() {
    CollectionMenuMockup(catalog = false, item = stringResource(R.string.mockup_my_teas_add_without_catalog))
}

private const val INFUSIONS = 3
private const val RING_PROGRESS = 0.2f
private val SEAL_TOP = 64.dp
private val SEAL_SIZE = 56.dp
