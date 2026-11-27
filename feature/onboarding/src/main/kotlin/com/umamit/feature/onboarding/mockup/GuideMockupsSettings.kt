package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PrimaryActionCard
import com.umamit.core.designsystem.component.ReliabilityBanner
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.onboarding.BandImage
import com.umamit.feature.onboarding.HOME_HEADER_RATIO
import com.umamit.feature.onboarding.R

/** M-SETTINGS-DATA: *Einstellungen*, the row *Daten & Sicherung* marked. */
@Composable
internal fun SettingsDataMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_settings_title), back = true)
        MockupListRow(text = null, chevron = true)
        MockupListRow(text = null, chevron = true, barWidth = BAR_SHORT)
        MockupListRow(
            text = stringResource(R.string.mockup_settings_data_backup),
            chevron = true,
            modifier = Modifier.mockupMarker(),
        )
        MockupListRow(text = null, chevron = true)
        MockupListRow(text = null, chevron = true, barWidth = BAR_SHORT)
    }
}

/** M-RELIABILITY: *Einstellungen*, the card *Timer-Zuverlässigkeit* with *Beheben* marked. */
@Composable
internal fun ReliabilityMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_settings_title), back = true)
        Column(
            Modifier
                .padding(horizontal = UmamiSpacing.Medium)
                .fillMaxWidth()
                .mockupMarker()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, UmamiShape.Card)
                .padding(UmamiSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            Text(
                stringResource(R.string.mockup_settings_reliability_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CheckLine(ok = true, barWidth = BAR_SHORT)
            CheckLine(ok = true, barWidth = BAR_MEDIUM)
            CheckLine(ok = false, barWidth = BAR_SHORT, fix = true)
        }
        MockupGap()
        MockupListRow(text = null, chevron = true)
        MockupListRow(text = null, chevron = true, barWidth = BAR_SHORT)
    }
}

@Composable
private fun CheckLine(ok: Boolean, barWidth: Dp, fix: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        Icon(
            painterResource(if (ok) UmamiIcons.Check else UmamiIcons.Warning),
            null,
            Modifier.size(ICON),
            if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Row(Modifier.weight(1f)) { MockupBar(barWidth) }
        if (fix) {
            Text(
                stringResource(R.string.mockup_settings_fix),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** M-BANNER: Start with the banner *Der Timer ist eventuell unzuverlässig* marked. */
@Composable
internal fun BannerMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.onboarding_app_name), settings = true)
        MockupGap(height = UmamiSpacing.XSmall)
        ReliabilityBanner(
            message = stringResource(R.string.mockup_home_reliability),
            onFix = {},
            modifier = Modifier.padding(horizontal = UmamiSpacing.Medium).mockupMarker(),
        )
        MockupGap(height = UmamiSpacing.XSmall)
        BandImage(
            res = if (UmamiTheme.isDark) UmamiIcons.HomeHeaderDark else UmamiIcons.HomeHeaderLight,
            ratio = HOME_HEADER_RATIO,
        )
        MockupGap()
        PrimaryActionCard(
            title = stringResource(R.string.mockup_home_start_brew),
            subtitle = stringResource(R.string.mockup_home_start_brew_subtitle),
            onClick = {},
            modifier = Modifier.padding(horizontal = UmamiSpacing.Medium),
        )
    }
}

private val BAR_SHORT = 100.dp
private val BAR_MEDIUM = 150.dp
private val ICON = 20.dp
