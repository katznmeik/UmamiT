package com.umamit.feature.brew.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.LocalOnAlertSurface
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.R

/** The filled circle behind ← and ⋮; the 48 dp touch target around it is unchanged (SCR-05). */
private val IconCircleSize = 40.dp
private const val ICON_CIRCLE_ALPHA = 0.6f

/** The colours of the timer's ← and ⋮: [icon] on a filled [circle] (SCR-05, D-093 point 2). */
@Immutable
internal data class TimerTopBarColors(val circle: Color, val icon: Color)

internal object TimerTopBarDefaults {
    /**
     * In the Steeping scheme the circle is the surface at 60 % opacity with the
     * light `onSurface` icon: over a pure white photo the circle is ≈ #7B807C
     * and the icon 3.3:1 on it; over the background or a dark photo 12–14:1.
     * On the [com.umamit.core.designsystem.component.AlertSurface] there is no
     * photo; the circle takes the alert's container colour, ink with a washi
     * icon like the *Abgegossen* pill (13.5:1), and stands out from both pulse
     * phases (13.5:1 on washi, 4.55:1 on matcha).
     */
    @Composable
    fun colors(): TimerTopBarColors {
        val alert = UmamiTheme.extendedColors.alert
        return if (LocalOnAlertSurface.current) {
            TimerTopBarColors(circle = alert.content, icon = alert.washi)
        } else {
            TimerTopBarColors(
                circle = MaterialTheme.colorScheme.surface.copy(alpha = ICON_CIRCLE_ALPHA),
                icon = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** ← and ⋮ over the top of the timer, each on its circle, legible on any tea photo (SCR-05). */
@Composable
internal fun TimerTopBar(state: TimerUiState.Active, overlays: TimerOverlays, actions: TimerActions) {
    val colors = TimerTopBarDefaults.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.XXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircledIconButton(
            painter = painterResource(UmamiIcons.ArrowBack),
            contentDescription = stringResource(R.string.brew_timer_back),
            colors = colors,
            onClick = actions.back,
        )
        Spacer(Modifier.weight(1f))
        Box {
            CircledIconButton(
                painter = painterResource(UmamiIcons.MoreVert),
                contentDescription = stringResource(R.string.brew_timer_more),
                colors = colors,
                onClick = overlays::openMenu,
            )
            // Not composed once the alert hides it: Material's menu would otherwise keep its popup for the
            // exit animation over the first frames of the alert (rule 8). A normal close still animates.
            if (overlays.menuComposable) {
                DropdownMenu(expanded = overlays.menuShown, onDismissRequest = { overlays.menuOpen = false }) {
                    state.menuItems().forEach { item ->
                        DropdownMenuItem(text = { Text(stringResource(item.label)) }, onClick = { actions.menu(item) })
                    }
                }
            }
        }
    }
}

/** A 48 dp icon button whose icon sits on a 40 dp filled circle. */
@Composable
private fun CircledIconButton(
    painter: Painter,
    contentDescription: String,
    colors: TimerTopBarColors,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.icon)) {
        Box(
            modifier = Modifier
                .size(IconCircleSize)
                .background(colors.circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter, contentDescription = contentDescription)
        }
    }
}

private val TimerMenuItem.label: Int
    get() = when (this) {
        TimerMenuItem.AdjustBrew -> R.string.brew_timer_menu_adjust
        TimerMenuItem.EndStepNow -> R.string.brew_timer_menu_end_step
        TimerMenuItem.SkipStep -> R.string.brew_timer_menu_skip
        TimerMenuItem.EditSteps -> R.string.brew_timer_menu_edit_steps
        TimerMenuItem.AddStep -> R.string.brew_timer_menu_add_step
        TimerMenuItem.FinishSession -> R.string.brew_timer_menu_finish
        TimerMenuItem.DiscardSession -> R.string.brew_timer_menu_discard
        TimerMenuItem.TimerSettings -> R.string.brew_timer_menu_settings
        TimerMenuItem.CancelQuickTimer -> R.string.brew_quick_cancel
    }
