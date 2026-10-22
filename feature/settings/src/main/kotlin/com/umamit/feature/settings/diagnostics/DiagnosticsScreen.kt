package com.umamit.feature.settings.diagnostics

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.data.diagnostics.DiagnosticsEntry
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.settings.R
import kotlinx.coroutines.launch

/**
 * What *Diagnose* shows: the log's [entries] newest first (null while it is
 * read) and the [header] that *Kopieren* puts above them (app version,
 * Android version, device), so a pasted log says where it came from.
 */
data class DiagnosticsUiState(val entries: List<DiagnosticsEntry>? = null, val header: String = "") {
    /** The text *Kopieren* puts on the clipboard: the header, then one entry per line as shown. */
    val copyText: String get() = (listOf(header) + entries.orEmpty().map { it.line }).joinToString("\n")
}

/** Test tags of the *Diagnose* screen. */
object DiagnosticsTags {
    const val MESSAGE = "diagnostics_message"
}

/** *Diagnose* with its view model (SCR-27, 06 §5.6). */
@Composable
fun DiagnosticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticsScreen(state = state, onBack = onBack, modifier = modifier)
}

/**
 * *Einstellungen › Über › Diagnose* (SCR-27, 06 §5.6, M1-20): the timer's
 * diagnostics log, newest first, each entry with its time in monospace, and
 * *Kopieren*, which puts the whole log on the clipboard to paste into an
 * issue. Android shows its own confirmation of the copy (Android 13+). The
 * entries are technical English (D-060); the screen around them is German.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(state: DiagnosticsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.diagnostics_clip_label)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = { BackAction(onBack) },
                actions = {
                    TextButton(
                        onClick = {
                            val clip = ClipData.newPlainText(clipLabel, state.copyText)
                            scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
                        },
                        enabled = !state.entries.isNullOrEmpty(),
                    ) {
                        Text(stringResource(R.string.diagnostics_copy))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        val entries = state.entries
        when {
            entries == null -> Box(Modifier.fillMaxSize().padding(padding))

            entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.diagnostics_empty_title),
                    body = stringResource(R.string.diagnostics_empty_body),
                )
            }

            else -> DiagnosticsList(entries, PaddingValues(horizontal = UmamiSpacing.SideMargin), padding)
        }
    }
}

@Composable
private fun DiagnosticsList(entries: List<DiagnosticsEntry>, sides: PaddingValues, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = UmamiSpacing.Large),
    ) {
        item {
            Text(
                text = stringResource(R.string.diagnostics_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(sides).padding(vertical = UmamiSpacing.Small),
            )
        }
        items(entries) { entry ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DiagnosticsRow(entry, Modifier.padding(sides))
        }
    }
}

@Composable
private fun DiagnosticsRow(entry: DiagnosticsEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = UmamiSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall),
    ) {
        Text(
            text = entry.time,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(DiagnosticsTags.MESSAGE),
        )
    }
}
