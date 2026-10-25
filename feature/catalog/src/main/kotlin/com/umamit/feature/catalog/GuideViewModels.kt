package com.umamit.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.catalog.GuideRepository
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the *Ratgeber* list shows (CAT-09).
 *
 * @property loading true until the guides were read once.
 */
data class GuideListUiState(val loading: Boolean = true, val guides: List<GuideSummary> = emptyList())

/** The *Ratgeber* list: the guides of the last successful refresh, offline (CAT-09). */
@HiltViewModel
class GuideListViewModel @Inject constructor(guides: GuideRepository) : ViewModel() {
    val state: StateFlow<GuideListUiState> = guides.observeGuides()
        .map { GuideListUiState(loading = false, guides = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GuideListUiState())
}

/**
 * What a guide's article shows (SCR-20).
 *
 * @property guide the guide, or null when the catalog holds none with this slug (any more).
 * @property storedSlugs the guides the catalog holds: a link to another one opens it in the app,
 *   a link to one it no longer holds opens the shop's page instead (D-087).
 */
data class GuideUiState(
    val loading: Boolean = true,
    val guide: GuideDetail? = null,
    val storedSlugs: Set<String> = emptySet(),
)

/** The guide [SLUG]'s article (SCR-20, CAT-09): read only from the local catalog, so it works offline. */
@HiltViewModel
class GuideViewModel @Inject constructor(savedState: SavedStateHandle, guides: GuideRepository) : ViewModel() {
    private val slug: String = checkNotNull(savedState[SLUG]) { "no guide slug" }

    val state: StateFlow<GuideUiState> = combine(guides.observeGuide(slug), guides.observeGuides()) { guide, all ->
        GuideUiState(loading = false, guide = guide, storedSlugs = all.map { it.slug }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GuideUiState())

    companion object {
        /** The navigation argument: the guide's slug (05 §4.3). */
        const val SLUG = "slug"
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
