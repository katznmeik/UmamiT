package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.RatingStars
import com.umamit.feature.onboarding.R

/** M-SETUP: *Neue Session* of Morgentau, the *Gefäß* row marked. */
@Composable
internal fun SetupMockup() {
    val tea = MockupDemo.morgentau
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_brew_setup_title), back = true)
        Column(
            Modifier.padding(horizontal = UmamiSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            MockupGap(height = UmamiSpacing.XSmall)
            Text(
                stringResource(tea.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MockupField(stringResource(R.string.mockup_brew_setup_vessel), Modifier.mockupMarker(), MockupDemo.vessel())
            MockupField(stringResource(R.string.mockup_brew_setup_leaf), value = MockupDemo.leaf())
            MockupField(stringResource(R.string.mockup_brew_setup_water), value = MockupDemo.water())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
            ) {
                Box(
                    Modifier.size(
                        SWITCH_WIDTH,
                        SWITCH_HEIGHT,
                    ).border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    stringResource(R.string.mockup_brew_setup_ratio_lock),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            MockupGap(height = UmamiSpacing.XSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PillButton(text = stringResource(R.string.mockup_brew_setup_start), onClick = {})
            }
        }
    }
}

/** M-TIMER-MENU: the timer with ⋮ open, *Schritte bearbeiten* marked. */
@Composable
internal fun TimerMenuMockup() {
    TimerMockupScreen(otherTimerRow = false) {
        MockupMenu(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = MENU_TOP, end = UmamiSpacing.XXSmall)
                .widthIn(max = MENU_MAX_WIDTH),
        ) {
            MockupMenuItem(stringResource(R.string.mockup_brew_timer_menu_edit_steps), Modifier.mockupMarker())
            MockupMenuItem(stringResource(R.string.mockup_brew_timer_menu_add_step))
            MockupMenuBar(MENU_BAR)
        }
    }
}

/** M-SAVED: *Gespeichert im Journal*, the two star rows marked. */
@Composable
internal fun SavedMockup() {
    val tea = MockupDemo.morgentau
    MockupScreen {
        Column(
            Modifier.fillMaxSize().padding(horizontal = UmamiSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MockupGap(height = SAVED_TOP)
            Icon(painterResource(UmamiIcons.Check), null, Modifier.size(SAVED_ICON), MaterialTheme.colorScheme.primary)
            MockupGap(height = UmamiSpacing.XSmall)
            Text(
                stringResource(R.string.mockup_journal_saved_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(tea.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MockupGap()
            Column(Modifier.mockupMarker()) {
                RatingStars(
                    value = TEA_RATING,
                    onChange = {},
                    label = stringResource(R.string.mockup_journal_rating_tea),
                    labelWidth = LABEL_WIDTH,
                )
                RatingStars(
                    value = BREWING_RATING,
                    onChange = {},
                    label = stringResource(R.string.mockup_journal_rating_brewing),
                    labelWidth = LABEL_WIDTH,
                )
            }
            MockupGap()
            MockupBars(listOf(BAR_LONG, BAR_MEDIUM))
        }
    }
}

private const val TEA_RATING = 4.0
private const val BREWING_RATING = 4.5
private val SWITCH_WIDTH = 40.dp
private val SWITCH_HEIGHT = 22.dp
private val MENU_TOP = 44.dp
private val MENU_MAX_WIDTH = 280.dp
private val MENU_BAR = 110.dp
private val SAVED_TOP = 72.dp
private val SAVED_ICON = 40.dp
private val LABEL_WIDTH = 110.dp
private val BAR_LONG = 220.dp
private val BAR_MEDIUM = 160.dp
