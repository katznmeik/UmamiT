package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.core.data.SettingsRepository
import com.umamit.feature.collection.CollectionSegment
import com.umamit.feature.collection.MyTeasActions
import com.umamit.feature.collection.MyTeasContent
import com.umamit.feature.collection.MyTeasRoute
import com.umamit.feature.collection.MyTeasUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import com.umamit.core.model.CollectionSegment as StoredSegment

/**
 * What Sammlung's *Meine Tees* segment gets from the shell (SCR-08, D-100).
 *
 * @property modifier its place below the segmented control.
 * @property scrollToTopRequest the tab's re-select counter (03 §2.2, D-049).
 * @property alerting whether an alert plays now: the FAB's choice and the chips' menus give way (rule 8).
 * @property openTea a row's tap: the tea's detail (SCR-09).
 * @property addFromCatalog *Aus dem Katalog* and *Im Katalog stöbern*: the *Katalog* segment (SCR-19).
 * @property addWithoutCatalog *Ohne Katalog*: *Sorte wählen* (SCR-11).
 */
internal data class MyTeasTab(
    val modifier: Modifier,
    val scrollToTopRequest: Int,
    val alerting: Boolean,
    val openTea: (teaId: String) -> Unit = {},
    val addFromCatalog: () -> Unit = {},
    val addWithoutCatalog: () -> Unit = {},
)

/** *Meine Tees* with its view model (#153). */
@Composable
internal fun MyTeasTabContent(tab: MyTeasTab) {
    MyTeasRoute(
        onOpenTea = tab.openTea,
        onAddFromCatalog = tab.addFromCatalog,
        onAddWithoutCatalog = tab.addWithoutCatalog,
        modifier = tab.modifier,
        alerting = tab.alerting,
        scrollToTopRequest = tab.scrollToTopRequest,
    )
}

/** *Meine Tees* without data, for tests and previews of the shell: „Noch keine Tees" with the FAB. */
@Composable
internal fun PlaceholderMyTeas(tab: MyTeasTab) {
    MyTeasContent(
        state = MyTeasUiState(loading = false),
        actions = MyTeasActions(
            onOpenTea = tab.openTea,
            onAddFromCatalog = tab.addFromCatalog,
            onAddWithoutCatalog = tab.addWithoutCatalog,
        ),
        modifier = tab.modifier,
        alerting = tab.alerting,
    )
}

/**
 * The Sammlung segment used last (03 §2.2, `collectionSegment`, 05 §6): the app's shell opens on it, and every
 * change is remembered across restarts.
 */
internal interface CollectionSegmentMemory {
    /** The segment used last; null until it is read. */
    val remembered: StateFlow<CollectionSegment?>

    fun remember(segment: CollectionSegment)

    /** Remembers nothing: *Meine Tees* (tests and previews). */
    object None : CollectionSegmentMemory {
        override val remembered: StateFlow<CollectionSegment?> = MutableStateFlow(CollectionSegment.MyTeas)

        override fun remember(segment: CollectionSegment) = Unit
    }
}

/** [CollectionSegmentMemory] in the settings; unreadable settings open *Meine Tees*, a failed write is dropped. */
internal class SettingsCollectionSegments(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : CollectionSegmentMemory {
    override val remembered: StateFlow<CollectionSegment?> = settings.settings
        .map { it.collectionSegment.toShell() }
        .catch { emit(CollectionSegment.MyTeas) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun remember(segment: CollectionSegment) {
        val stored = segment.toStored()
        scope.launch {
            try {
                settings.update { if (it.collectionSegment == stored) it else it.copy(collectionSegment = stored) }
            } catch (_: IOException) {
                // The segment is a convenience: the next start opens the one remembered before.
            }
        }
    }

    private fun StoredSegment.toShell() = when (this) {
        StoredSegment.MY_TEAS -> CollectionSegment.MyTeas
        StoredSegment.CATALOG -> CollectionSegment.Catalog
    }

    private fun CollectionSegment.toStored() = when (this) {
        CollectionSegment.MyTeas -> StoredSegment.MY_TEAS
        CollectionSegment.Catalog -> StoredSegment.CATALOG
    }
}
