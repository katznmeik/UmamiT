package com.umamit.feature.teaware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.VesselArchive
import com.umamit.core.data.VesselRepository
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** A vessel as SCR-13 lists it: type icon, name and volume. */
data class VesselRow(
    val id: String,
    val name: String,
    val type: VesselType,
    val capacityMl: Int,
    /** The usable volume (WARE-04), only when it differs from [capacityMl]. */
    val usableMl: Int?,
    val isFavorite: Boolean,
)

/** SCR-13's state. */
sealed interface TeawareUiState {
    data object Loading : TeawareUiState

    /** [vessels]: favourites first, then A–Z; [archived] below under „ARCHIVIERT", A–Z. */
    data class Ready(val vessels: List<VesselRow>, val archived: List<VesselRow>) : TeawareUiState {
        val isEmpty: Boolean get() = vessels.isEmpty() && archived.isEmpty()
    }
}

/**
 * The teaware list (SCR-13; WARE-01, D-100), from *Einstellungen › Teegeschirr*: the vessels with
 * favourites first, then A–Z; the archived ones in their own part with *Wiederherstellen*.
 */
@HiltViewModel
class TeawareViewModel @Inject constructor(vessels: VesselRepository, private val archive: VesselArchive) :
    ViewModel() {

    val state: StateFlow<TeawareUiState> = vessels.observeVessels()
        .map { all ->
            val (archived, active) = all.partition { it.isArchived }
            TeawareUiState.Ready(
                vessels = active.sortedWith(compareByDescending<Vessel> { it.isFavorite }.thenBy(byName) { it.name })
                    .map { it.row() },
                archived = archived.sortedWith(compareBy(byName) { it.name }).map { it.row() },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TeawareUiState.Loading)

    /** *Wiederherstellen*, and *Rückgängig* after the editor's *Archivieren*. */
    fun restore(vesselId: String) {
        viewModelScope.launch { archive.restore(vesselId) }
    }

    private companion object {
        /** German A–Z: „Öko" next to „Oolong", case and accents second (GEN-10's spirit). */
        val byName: Comparator<String> = Collator.getInstance(Locale.GERMAN)
            .apply { strength = Collator.SECONDARY }
            .let { collator -> Comparator { a, b -> collator.compare(a.trim(), b.trim()) } }
    }
}

private fun Vessel.row() = VesselRow(
    id = id,
    name = name,
    type = type,
    capacityMl = capacityMl,
    usableMl = usableMl?.takeIf { it != capacityMl },
    isFavorite = isFavorite,
)
