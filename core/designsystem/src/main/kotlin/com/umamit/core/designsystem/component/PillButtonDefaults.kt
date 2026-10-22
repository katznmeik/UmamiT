package com.umamit.core.designsystem.component

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.umamit.core.designsystem.theme.UmamiTheme

/** Colours of a [PillButton]. */
object PillButtonDefaults {
    /**
     * Cream on dark, green on light (03 §3.8). On an [AlertSurface] the pill is
     * ink with washi text: 13.5:1 for the label, and the pill itself stands out
     * from both pulse phases (13.5:1 on washi, 4.55:1 on matcha), so the only
     * control never fades into the pulse (rule 8).
     */
    @Composable
    fun colors(): ButtonColors {
        val alert = UmamiTheme.extendedColors.alert
        return when {
            LocalOnAlertSurface.current -> ButtonDefaults.buttonColors(
                containerColor = alert.content,
                contentColor = alert.washi,
            )

            UmamiTheme.isDark -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            else -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
