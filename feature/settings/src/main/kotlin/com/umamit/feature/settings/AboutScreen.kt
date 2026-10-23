package com.umamit.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * Über (SCR-27): logo, version, the Tee-Kontor source note, the licences, *Einrichtung erneut
 * zeigen* (ONB-04) and *Diagnose* (06 §5.6, D-060).
 */
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    onRerunSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(R.string.about_title),
        modifier = modifier,
        navigationIcon = { BackAction(onBack) },
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(UmamiSpacing.Large))
        Icon(
            painter = painterResource(UmamiIcons.Leaf),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        Text(
            text = stringResource(R.string.about_source_note),
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(UmamiSpacing.Large))
        SettingsRow(
            title = stringResource(R.string.about_rerun_setup),
            onClick = onRerunSetup,
            chevron = true,
        )
        SettingsRow(
            title = stringResource(R.string.about_diagnostics),
            value = stringResource(R.string.about_diagnostics_detail),
            onClick = onOpenDiagnostics,
            chevron = true,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.about_licenses_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_licenses_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
