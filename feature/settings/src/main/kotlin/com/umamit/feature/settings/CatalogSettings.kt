package com.umamit.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.CatalogRefreshBar
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.catalogRefreshProgressLine
import com.umamit.core.ui.catalogRefreshResultLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The catalog row of SCR-25 (SET-23).
 *
 * @property lastRefreshAtMillis when the last successful refresh finished (the refresh log); null before the first.
 * @property progress the running refresh's progress, whoever started it; null when none runs.
 * @property result how the refresh ended, shown until the settings are left; null before and while one runs.
 */
data class CatalogSettingsState(
    val lastRefreshAtMillis: Long? = null,
    val progress: CatalogRefreshProgress? = null,
    val result: CatalogRefreshOutcome? = null,
) {
    /** A refresh runs now. */
    val refreshing: Boolean get() = progress != null
}

/** SET-23: the last refresh, *Jetzt aktualisieren* and the refresh's progress and result under it (CAT-07 AC7). */
@HiltViewModel
class CatalogSettingsViewModel @Inject constructor(
    repository: CatalogRepository,
    private val refresh: CatalogRefreshRunner,
) : ViewModel() {
    /**
     * Whether a refresh ran while these settings were open: a result the runner holds from before is not shown
     * again; only the date is (SET-23).
     */
    private val sawRunning = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            refresh.state.collect { if (it is CatalogRefreshState.Running) sawRunning.value = true }
        }
    }

    val state: StateFlow<CatalogSettingsState> =
        combine(repository.observeLastRefresh(), refresh.state, sawRunning) { last, run, saw ->
            CatalogSettingsState(
                lastRefreshAtMillis = last,
                progress = (run as? CatalogRefreshState.Running)?.progress,
                result = (run as? CatalogRefreshState.Finished)?.outcome?.takeIf { saw },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogSettingsState())

    /** *Jetzt aktualisieren*: the refresh runs in its own service with a notification (06 §5.3). */
    fun refreshNow() = refresh.start()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * *Katalog* in the settings (SET-23): „Letzter Abruf" with the date of the last successful refresh,
 * and *Jetzt aktualisieren*, which waits while a refresh runs. Under it the running refresh's progress bar
 * and line and then its result line, the same texts as SCR-22's sheet; no *Abbrechen* here. They take their
 * height with the fold's tokens (03 §3.6), at once under reduce motion.
 */
@Composable
internal fun CatalogSection(state: CatalogSettingsState, onRefresh: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    Column(modifier = Modifier.fillMaxWidth().testTag(SettingsTags.CATALOG)) {
        val last = state.lastRefreshAtMillis
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_catalog_last_refresh)) },
            supportingContent = {
                Text(
                    when {
                        state.refreshing -> stringResource(R.string.settings_catalog_refreshing)
                        last != null -> GermanFormat.day(last, zone)
                        else -> stringResource(R.string.settings_catalog_never)
                    },
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.XXSmall),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        ) {
            OutlinedButton(onClick = onRefresh, enabled = !state.refreshing) {
                Text(stringResource(R.string.settings_catalog_refresh_now))
            }
            RefreshStatus(state)
            Text(
                text = stringResource(R.string.settings_catalog_refresh_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The progress bar and line while a refresh runs, its result line after it; nothing otherwise (SET-23). */
@Composable
private fun RefreshStatus(state: CatalogSettingsState) {
    val motion = UmamiTheme.motion
    AnimatedVisibility(
        visible = state.progress != null || state.result != null,
        enter = expandVertically(motion.sheet()) + fadeIn(motion.fade()),
        exit = shrinkVertically(motion.sheet()) + fadeOut(motion.exitFade()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = UmamiSpacing.XSmall)
                .testTag(SettingsTags.CATALOG_REFRESH),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            val progress = state.progress
            val result = state.result
            if (progress != null) {
                // The bar says nothing to TalkBack: the counts are not announced as they change.
                CatalogRefreshBar(progress, Modifier.clearAndSetSemantics {})
                RefreshLine(catalogRefreshProgressLine(progress), Modifier.testTag(SettingsTags.CATALOG_REFRESH_LINE))
            } else if (result != null) {
                // The result is announced once, politely, when it appears.
                RefreshLine(
                    catalogRefreshResultLine(result),
                    Modifier
                        .testTag(SettingsTags.CATALOG_REFRESH_LINE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun RefreshLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
