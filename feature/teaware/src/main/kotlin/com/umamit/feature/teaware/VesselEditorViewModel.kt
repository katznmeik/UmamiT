package com.umamit.feature.teaware

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.ArchiveResult
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SetVesselPhotoResult
import com.umamit.core.data.VesselArchive
import com.umamit.core.data.VesselPhotos
import com.umamit.core.data.VesselRepository
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselMaterial
import com.umamit.core.model.VesselType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The editor's fields as typed (SCR-14): the volumes as text, so a half-typed number stays as it is. */
data class VesselForm(
    val name: String = "",
    val type: VesselType = VesselType.KYUSU,
    val capacity: String = "",
    val usable: String = "",
    val material: VesselMaterial? = null,
    val notes: String = "",
    val isFavorite: Boolean = false,
) {
    /** What *Speichern* refuses (WARE-01, 05 §3.4). */
    fun errors(): Set<VesselFieldError> = buildSet {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) add(VesselFieldError.NameEmpty)
        if (trimmed.length > MAX_NAME) add(VesselFieldError.NameTooLong)
        val capacityMl = capacity.toMl()
        if (capacityMl == null || capacityMl !in VOLUME_RANGE) add(VesselFieldError.CapacityRange)
        if (usable.isNotBlank()) {
            val usableMl = usable.toMl()
            val max = capacityMl?.takeIf { it in VOLUME_RANGE } ?: VOLUME_RANGE.last
            if (usableMl == null || usableMl !in VOLUME_RANGE.first..max) add(VesselFieldError.UsableRange)
        }
    }

    /** [base] with this form's fields; call only when [errors] is empty. */
    internal fun applyTo(base: Vessel): Vessel = base.copy(
        name = name.trim(),
        type = type,
        capacityMl = checkNotNull(capacity.toMl()),
        usableMl = usable.toMl(),
        material = material,
        notes = notes.trim().ifEmpty { null },
        isFavorite = isFavorite,
    )

    companion object {
        /** A vessel's name has 1–40 characters (05 §3.4). */
        const val MAX_NAME = 40

        /** Volume and usable volume, in ml (WARE-01). */
        val VOLUME_RANGE = 10..3000

        fun of(vessel: Vessel) = VesselForm(
            name = vessel.name,
            type = vessel.type,
            capacity = vessel.capacityMl.toString(),
            usable = vessel.usableMl?.toString().orEmpty(),
            material = vessel.material,
            notes = vessel.notes.orEmpty(),
            isFavorite = vessel.isFavorite,
        )

        private fun String.toMl(): Int? = trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
    }
}

/** The photo field's picture (SCR-14, WARE-01): the vessel's own, one just picked, or none. */
sealed interface VesselPhoto {
    data object None : VesselPhoto

    /** The vessel's stored photo, at [path] in `photos/`. */
    data class Stored(val path: String) : VesselPhoto

    /** A photo picked in this editor, [uri] from the system picker; stored with *Speichern*. */
    data class Picked(val uri: String) : VesselPhoto
}

/** A field *Speichern* refuses, shown at the field. */
enum class VesselFieldError { NameEmpty, NameTooLong, CapacityRange, UsableRange }

/** SCR-14's state. */
sealed interface VesselEditorUiState {
    data object Loading : VesselEditorUiState

    /** The vessel to edit is gone. */
    data object NotFound : VesselEditorUiState

    /**
     * @property errors the refused fields, once *Speichern* was tapped (then kept up to date as you type).
     * @property saving *Speichern* is stored and the editor is closing: *Speichern* is greyed out (a second tap
     *   would store a second vessel).
     * @property archiveBlocked the vessel belongs to the active brew session: *Archivieren* is greyed out (E10).
     * @property photo the photo field's picture; a pick or a removal counts as a change but is stored only with
     *   *Speichern*, like every other field.
     */
    data class Ready(
        val form: VesselForm,
        val isNew: Boolean,
        val isArchived: Boolean,
        val archiveBlocked: Boolean,
        val hasChanges: Boolean,
        val errors: Set<VesselFieldError>,
        val saving: Boolean = false,
        val photo: VesselPhoto = VesselPhoto.None,
    ) : VesselEditorUiState
}

/** What the editor asks its host to do. */
sealed interface VesselEditorEvent {
    /** *Speichern* stored the vessel: close and hand [vesselId] back (the setup's *Neues Gefäß …* selects it). */
    data class Saved(val vesselId: String) : VesselEditorEvent

    /** *Archivieren*: close; the list below shows „*[name]* archiviert" · *Rückgängig* (D-100). */
    data class Archived(val vesselId: String, val name: String) : VesselEditorEvent

    /** The picked image could not be read at *Speichern* (grant gone or no image): a toast; the rest is saved. */
    data object PhotoUnreadable : VesselEditorEvent
}

/**
 * The vessel editor (SCR-14; WARE-01, WARE-04, D-100): a new vessel when the route has no [VESSEL_ID],
 * else the stored one, the seed vessels included. Nothing is stored before *Speichern*; a volume edit
 * changes no recipe (a recipe stores its water). ⋮ *Archivieren* (never delete) is refused for the vessel
 * of the active brew session; an archived vessel offers *Wiederherstellen*. The form lives in the
 * [SavedStateHandle], so it survives a process death. The vessel's one photo (WARE-01) is set, replaced or
 * removed through [VesselPhotos] after the vessel itself is stored; a photo that can't be read then is skipped,
 * with a short toast.
 */
@Suppress("TooManyFunctions") // the form, the photo change and the three menu actions each have a few
@HiltViewModel
class VesselEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val vessels: VesselRepository,
    private val archive: VesselArchive,
    private val photos: VesselPhotos,
    private val photoFiles: PhotoFiles,
) : ViewModel() {

    private val vesselId: String? = savedState[VESSEL_ID]

    /** The stored vessel, or a new one; null while loading. */
    private val base = MutableStateFlow<Base?>(null)
    private val form = MutableStateFlow(restoreForm())
    private val showErrors = MutableStateFlow(savedState[SHOW_ERRORS] ?: false)
    private val saving = MutableStateFlow(false)
    private val photoChange = MutableStateFlow(restorePhotoChange())
    private val navigation = Channel<VesselEditorEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<VesselEditorEvent> = navigation.receiveAsFlow()

    private val flags = combine(showErrors, saving, photoChange, ::Flags)

    val state: StateFlow<VesselEditorUiState> = combine(
        base,
        form,
        flags,
        archive.observeActiveVesselId(),
    ) { b, f, flag, activeId ->
        when {
            b == null -> VesselEditorUiState.Loading

            b is Base.Gone -> VesselEditorUiState.NotFound

            f == null -> VesselEditorUiState.Loading

            else -> {
                val stored = (b as? Base.Stored)?.vessel
                VesselEditorUiState.Ready(
                    form = f,
                    isNew = stored == null,
                    isArchived = stored?.isArchived == true,
                    archiveBlocked = stored != null && stored.id == activeId,
                    hasChanges = f != (stored?.let(VesselForm::of) ?: VesselForm()) || flag.photo != PhotoChange.Keep,
                    errors = if (flag.showErrors) f.errors() else emptySet(),
                    saving = flag.saving,
                    photo = photoOf(stored, flag.photo),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VesselEditorUiState.Loading)

    init {
        viewModelScope.launch {
            val loaded = if (vesselId == null) Base.New else vessels.getVessel(vesselId)?.let(Base::Stored) ?: Base.Gone
            if (form.value == null) {
                when (loaded) {
                    Base.New -> setForm(VesselForm())
                    is Base.Stored -> setForm(VesselForm.of(loaded.vessel))
                    Base.Gone -> Unit
                }
            }
            base.value = loaded
        }
    }

    fun update(transform: (VesselForm) -> VesselForm) {
        form.value?.let { setForm(transform(it)) }
    }

    /** *Speichern*: stores the vessel when every field is valid, else shows the errors at the fields. */
    fun save() {
        if (saving.value) return
        val f = form.value
        val stored = when (val b = base.value) {
            is Base.Stored -> b.vessel
            Base.New -> Vessel(UUID.randomUUID().toString(), "", f?.type ?: VesselType.KYUSU, 0)
            Base.Gone, null -> null
        }
        when {
            f == null || stored == null -> Unit

            f.errors().isNotEmpty() -> {
                showErrors.value = true
                savedState[SHOW_ERRORS] = true
            }

            else -> {
                val vessel = f.applyTo(stored)
                saving.value = true
                viewModelScope.launch {
                    vessels.saveVessel(vessel)
                    val photoUnreadable = applyPhoto(vessel.id)
                    if (photoUnreadable) navigation.send(VesselEditorEvent.PhotoUnreadable)
                    base.value = Base.Stored(vessel)
                    navigation.send(VesselEditorEvent.Saved(vessel.id))
                }
            }
        }
    }

    /** The photo picker returned [uri]: it is shown at once and replaces the vessel's photo with *Speichern*. */
    fun pickPhoto(uri: String) {
        setPhotoChange(PhotoChange.Set(uri))
    }

    /** *Foto entfernen*: a stored photo goes with *Speichern*; one that was only picked is dropped. */
    fun removePhoto() {
        val hasStored = (base.value as? Base.Stored)?.vessel?.coverPhotoId != null
        setPhotoChange(if (hasStored) PhotoChange.Remove else PhotoChange.Keep)
    }

    /** Applies the photo change; true when a picked image could not be read. */
    private suspend fun applyPhoto(vesselId: String): Boolean {
        when (val change = photoChange.value) {
            PhotoChange.Keep -> Unit
            is PhotoChange.Set -> return photos.set(vesselId, change.uri) == SetVesselPhotoResult.Unreadable
            PhotoChange.Remove -> photos.remove(vesselId)
        }
        return false
    }

    private fun photoOf(stored: Vessel?, change: PhotoChange): VesselPhoto = when (change) {
        is PhotoChange.Set -> VesselPhoto.Picked(change.uri)

        PhotoChange.Remove -> VesselPhoto.None

        PhotoChange.Keep ->
            stored?.coverPhotoId
                ?.let { photoFiles.file(it) }
                ?.takeIf { it.isFile }
                ?.let { VesselPhoto.Stored(it.path) }
                ?: VesselPhoto.None
    }

    private fun setPhotoChange(change: PhotoChange) {
        photoChange.value = change
        savedState[PHOTO_CHANGE] = when (change) {
            PhotoChange.Keep -> null
            PhotoChange.Remove -> PHOTO_REMOVE
            is PhotoChange.Set -> PHOTO_SET + change.uri
        }
    }

    private fun restorePhotoChange(): PhotoChange = savedState.get<String>(PHOTO_CHANGE).let { saved ->
        when {
            saved == null -> PhotoChange.Keep
            saved == PHOTO_REMOVE -> PhotoChange.Remove
            saved.startsWith(PHOTO_SET) -> PhotoChange.Set(saved.removePrefix(PHOTO_SET))
            else -> PhotoChange.Keep
        }
    }

    /** ⋮ *Archivieren* of the stored vessel; nothing for the active brew session's vessel (E10). */
    fun archive() {
        val stored = (base.value as? Base.Stored)?.vessel ?: return
        viewModelScope.launch {
            if (archive.archive(stored.id) == ArchiveResult.Archived) {
                navigation.send(VesselEditorEvent.Archived(stored.id, stored.name))
            }
        }
    }

    /** ⋮ *Wiederherstellen* of an archived vessel: it is back in the pickers; the editor stays open. */
    fun restore() {
        val stored = (base.value as? Base.Stored)?.vessel ?: return
        viewModelScope.launch {
            archive.restore(stored.id)
            base.update { (it as? Base.Stored)?.let { s -> Base.Stored(s.vessel.copy(isArchived = false)) } ?: it }
        }
    }

    private fun setForm(value: VesselForm) {
        form.value = value
        savedState[FORM_NAME] = value.name
        savedState[FORM_TYPE] = value.type.name
        savedState[FORM_CAPACITY] = value.capacity
        savedState[FORM_USABLE] = value.usable
        savedState[FORM_MATERIAL] = value.material?.name
        savedState[FORM_NOTES] = value.notes
        savedState[FORM_FAVORITE] = value.isFavorite
    }

    private fun restoreForm(): VesselForm? {
        val name = savedState.get<String>(FORM_NAME) ?: return null
        return VesselForm(
            name = name,
            type = savedState.get<String>(FORM_TYPE)?.let { name -> VesselType.entries.find { it.name == name } }
                ?: VesselType.KYUSU,
            capacity = savedState[FORM_CAPACITY] ?: "",
            usable = savedState[FORM_USABLE] ?: "",
            material = savedState.get<String>(FORM_MATERIAL)
                ?.let { name -> VesselMaterial.entries.find { it.name == name } },
            notes = savedState[FORM_NOTES] ?: "",
            isFavorite = savedState[FORM_FAVORITE] ?: false,
        )
    }

    /** What *Speichern* does to the vessel's photo. */
    private sealed interface PhotoChange {
        data object Keep : PhotoChange

        data object Remove : PhotoChange

        data class Set(val uri: String) : PhotoChange
    }

    private data class Flags(val showErrors: Boolean, val saving: Boolean, val photo: PhotoChange)

    private sealed interface Base {
        data object New : Base

        data object Gone : Base

        data class Stored(val vessel: Vessel) : Base
    }

    companion object {
        /** The route argument: the vessel to edit; absent for a new vessel. */
        const val VESSEL_ID = "vesselId"

        private const val SHOW_ERRORS = "vesselEditor.showErrors"
        private const val PHOTO_CHANGE = "vesselEditor.photo"
        private const val PHOTO_REMOVE = "remove"
        private const val PHOTO_SET = "set:"
        private const val FORM_NAME = "vesselEditor.name"
        private const val FORM_TYPE = "vesselEditor.type"
        private const val FORM_CAPACITY = "vesselEditor.capacity"
        private const val FORM_USABLE = "vesselEditor.usable"
        private const val FORM_MATERIAL = "vesselEditor.material"
        private const val FORM_NOTES = "vesselEditor.notes"
        private const val FORM_FAVORITE = "vesselEditor.favorite"
    }
}
