package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.AddPhotoResult
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.PhotoFileContext
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** What the hero tells the page once: a photo that couldn't be added or deleted (SCR-28's errors). */
enum class SessionHeroEvent {
    /** *+ Foto*: the image can't be read; nothing was added („Das Foto konnte nicht gelesen werden."). */
    PHOTO_UNREADABLE,

    /** *+ Foto*: the session has 6 photos already; nothing was added („Höchstens 6 Fotos"). */
    PHOTO_LIMIT_REACHED,

    /** *+ Foto*: the photo couldn't be stored; nothing was added. */
    PHOTO_FAILED,

    /** *Löschen*: the write failed; nothing changed („Das Foto konnte nicht geändert werden."). */
    PHOTO_CHANGE_FAILED,
}

/**
 * The session page's hero (SCR-16, JRN-07, D-112) for the session `sessionId`: its own photos through
 * [SessionPhotos], and the tea's cover from the journal's read for a session without them. The files are checked off
 * the main thread ([PhotoFileContext]). A photo added or deleted is written at once; a refusal or failure is said once
 * through [events]. Kept apart from [SessionPageViewModel] so the photos' seam stays on its own.
 */
@HiltViewModel
class SessionHeroViewModel @Inject constructor(
    savedState: SavedStateHandle,
    journal: JournalRepository,
    private val photos: SessionPhotos,
    private val files: PhotoFiles,
    @PhotoFileContext private val photoFileContext: CoroutineContext = EmptyCoroutineContext,
) : ViewModel() {
    private val sessionId: String =
        checkNotNull(savedState[SessionPageViewModel.SESSION_ID]) { "the session hero needs a sessionId" }

    private val _events = Channel<SessionHeroEvent>(Channel.BUFFERED)

    /** [SessionHeroEvent]s for the page, each delivered once. */
    val events: Flow<SessionHeroEvent> = _events.receiveAsFlow()

    /** The hero; null until its first read, so the page draws the bare tint rather than the leaf meanwhile. */
    val state: StateFlow<SessionHero?> = combine(
        journal.observeSession(sessionId)
            .map { it?.coverPhoto }
            .distinctUntilChanged(),
        photos.observePhotos(sessionId),
    ) { cover, list ->
        SessionHero(
            photos = list.mapNotNull { row ->
                files.file(row.id).takeIf { it.isFile }?.let { SessionHeroPhoto(row.id, it.path) }
            },
            photoCount = list.size,
            coverPath = cover?.takeIf { it.isFile }?.path,
        )
    }
        .distinctUntilChanged()
        .flowOn(photoFileContext) // the photo files are checked off the main thread (the app provides IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** *+ Foto*: adds the image the photo picker returned (JRN-07); a refusal or failure is said once. */
    fun addPhoto(uri: String) {
        viewModelScope.launch {
            when (photos.add(sessionId, uri)) {
                is AddPhotoResult.Added -> Unit
                AddPhotoResult.LimitReached -> _events.send(SessionHeroEvent.PHOTO_LIMIT_REACHED)
                AddPhotoResult.Unreadable -> _events.send(SessionHeroEvent.PHOTO_UNREADABLE)
                AddPhotoResult.Failed -> _events.send(SessionHeroEvent.PHOTO_FAILED)
            }
        }
    }

    /** The confirmation's *Löschen* (JRN-07): photo [photoId] goes; the pager shows its neighbour or the cover. */
    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            if (!photos.delete(sessionId, photoId)) _events.send(SessionHeroEvent.PHOTO_CHANGE_FAILED)
        }
    }
}
