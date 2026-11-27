package com.umamit.feature.onboarding.howto

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.onboarding.R
import com.umamit.core.designsystem.R as DsR

/**
 * Anleitungen, the list (SCR-35; ONB-08): ← and the title, then one row per [HowToTopic] with its title and its
 * one-line summary, in the spec's order. A tap calls [onOpenTopic]; ← and back call [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToListScreen(onOpenTopic: (HowToTopic) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.howto_list_title),
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag(HowToTags.LIST),
        ) {
            HowToTopic.entries.forEach { topic ->
                HowToRow(topic, onClick = { onOpenTopic(topic) })
            }
        }
    }
}

/** ← in a top bar, with the design system's „Zurück" / „Back" for TalkBack. */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(UmamiIcons.ArrowBack), stringResource(DsR.string.ds_back))
    }
}

@Composable
private fun HowToRow(topic: HowToTopic, onClick: () -> Unit) {
    Column(Modifier.testTag(HowToTags.row(topic))) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(topic.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(topic.line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(UmamiIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
