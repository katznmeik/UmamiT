package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionPhotos
import com.umamit.core.testing.FakeSessionPhotos
import com.umamit.core.testing.FakeSessions
import java.io.File

/** The hero's view model of session [id] on [sessions]; without [photos] the session has none (JRN-07). */
internal fun heroViewModel(
    sessions: FakeSessions,
    id: String,
    photos: SessionPhotos = FakeSessionPhotos(sessions),
    files: PhotoFiles = PhotoFiles(File("no-photos")),
) = SessionHeroViewModel(SavedStateHandle(mapOf("sessionId" to id)), sessions.journal, photos, files)
