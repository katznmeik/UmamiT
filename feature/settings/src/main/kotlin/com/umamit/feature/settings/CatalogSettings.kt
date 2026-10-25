package com.umamit.feature.settings

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.designsystem.theme.UmamiSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The catalog row of SCR-25 (SET-23).
 *
 * @property lastRefreshAtMillis when the last successful refresh finished (the refresh log); null before the first.
 * @property refreshing a refresh runs now.
 */
data class CatalogSettingsState(val lastRefreshAtMillis: Long? = null, val refreshing: Boolean = false)

/** SET-23: the last refresh and *Jetzt aktualisieren*, which starts the refresh only on this tap (CAT-07 AC7). */
@HiltViewModel
class CatalogSettingsViewModel @Inject constructor(
    repository: CatalogRepository,
    private val refresh: CatalogRefreshRunner,
) : ViewModel() {
    val state: StateFlow<CatalogSettingsState> = combine(repository.observeLastRefresh(), refresh.state) { last, run ->
        CatalogSettingsState(lastRefreshAtMillis = last, refreshing = run is CatalogRefreshState.Running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogSettingsState())

    /** *Jetzt aktualisieren*: the refresh runs in its own service with a notification (06 §5.3). */
    fun refreshNow() = refresh.start()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** „21. Okt. 2026" (CLAUDE.md rule 4). */
private val DATE = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/**
 * *Katalog* in the settings (SET-23): „Letzter Abruf" with the date of the last successful refresh,
 * and *Jetzt aktualisieren*, which waits while a refresh runs. Progress and result show in the
 * refresh's notification (CAT-07 AC1).
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
                        last != null -> DATE.format(Instant.ofEpochMilli(last).atZone(zone))
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
            Text(
                text = stringResource(R.string.settings_catalog_refresh_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
