package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.onboarding.R

/** M-STEPS: *Rezept bearbeiten*, the kind menu of a step open and marked. */
@Composable
internal fun StepsMockup() {
    Box(Modifier.fillMaxSize()) {
        MockupScreen {
            MockupTopBar(title = stringResource(R.string.mockup_recipe_editor_title_edit), back = true)
            MockupGap(height = UmamiSpacing.XSmall)
            StepRow(1, R.string.mockup_recipe_step_kind_rinse, "0:30")
            StepRow(2, R.string.mockup_recipe_step_kind_infusion, MockupDemo.steepTime())
            StepRow(THIRD_STEP, R.string.mockup_recipe_step_kind_infusion, "0:45")
            Row(
                Modifier.padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            ) {
                Icon(painterResource(UmamiIcons.Add), null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.mockup_recipe_step_add),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        MockupMenu(Modifier.align(Alignment.TopStart).offset(x = KIND_MENU_X, y = KIND_MENU_Y).mockupMarker()) {
            MockupMenuItem(stringResource(R.string.mockup_recipe_step_kind_infusion))
            MockupMenuItem(stringResource(R.string.mockup_recipe_step_kind_rinse))
            MockupMenuItem(stringResource(R.string.mockup_recipe_step_kind_whisk))
        }
    }
}

@Composable
private fun StepRow(number: Int, kind: Int, time: String) {
    Row(
        Modifier.fillMaxWidth().height(STEP_HEIGHT).padding(horizontal = UmamiSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.weight(
                1f,
            ).border(
                UmamiSpacing.Hairline,
                MaterialTheme.colorScheme.outline,
                CircleShape,
            ).padding(horizontal = UmamiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(kind),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painterResource(UmamiIcons.ChevronRight),
                null,
                Modifier.size(STEP_ICON),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            MockupDemo.temperature(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** M-RECEIVE: *Rezept übernehmen*, the question *Für welchen Tee?* marked. */
@Composable
internal fun ReceiveMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(R.string.mockup_receive_recipe_title), back = true)
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium).padding(top = UmamiSpacing.XSmall)) {
            MockupBars(listOf(BAR_LONG, BAR_MEDIUM, BAR_LONG))
        }
        Column(Modifier.mockupMarker()) {
            SectionHeader(stringResource(R.string.mockup_receive_recipe_which_tea))
            MockupDemo.teas.forEachIndexed { index, tea ->
                Row(
                    Modifier.fillMaxWidth().height(STEP_HEIGHT).padding(horizontal = UmamiSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
                ) {
                    Radio(selected = index == 0)
                    Text(
                        stringResource(tea.name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        MockupGap()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillButton(text = stringResource(R.string.mockup_receive_recipe_title), onClick = {})
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        Modifier.size(RADIO_SIZE).border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(RADIO_DOT).border(RADIO_DOT, MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

private val BAR_LONG = 220.dp
private val BAR_MEDIUM = 160.dp
private val DAY_BAR = 70.dp
private val STEP_HEIGHT = 48.dp
private val STEP_ICON = 18.dp
private val KIND_MENU_X = 40.dp
private val KIND_MENU_Y = 120.dp
private val RADIO_SIZE = 20.dp
private val RADIO_DOT = 10.dp

private const val THIRD_STEP = 3
