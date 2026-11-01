package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * The Sammlung segment the shell shows. The app's shell ([ShellOpening.Start]) opens on the one used last once
 * [CollectionSegmentMemory.remembered] is read; a change before that wins. Every change is remembered.
 */
@Stable
internal class SegmentChoice(segment: CollectionSegment, known: Boolean) {
    var segment by mutableStateOf(segment)
        private set

    /** Whether [segment] is the remembered one or a choice; until then it is not written back. */
    var known by mutableStateOf(known)
        private set

    fun choose(segment: CollectionSegment) {
        this.segment = segment
        known = true
    }

    /** The segment used last, read after the shell was shown: taken unless one was chosen meanwhile. */
    fun adopt(remembered: CollectionSegment?) {
        if (!known && remembered != null) choose(remembered)
    }

    companion object {
        val Saver = listSaver<SegmentChoice, Any>(
            save = { listOf(it.segment.name, it.known) },
            restore = { (segment, known) ->
                SegmentChoice(CollectionSegment.valueOf(segment as String), known as Boolean)
            },
        )
    }
}

@Composable
internal fun rememberSegmentChoice(opening: ShellOpening, memory: CollectionSegmentMemory): SegmentChoice {
    val remembered by memory.remembered.collectAsStateWithLifecycle()
    val choice = rememberSaveable(saver = SegmentChoice.Saver) {
        SegmentChoice(opening.firstSegment(remembered), known = opening !is ShellOpening.Start || remembered != null)
    }
    LaunchedEffect(remembered) { choice.adopt(remembered) }
    LaunchedEffect(choice.segment, choice.known) { if (choice.known) memory.remember(choice.segment) }
    return choice
}
