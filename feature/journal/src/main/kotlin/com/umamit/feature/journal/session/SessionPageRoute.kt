package com.umamit.feature.journal.session

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.ui.PhotoPicker
import com.umamit.core.ui.SystemPhotoPicker
import com.umamit.core.ui.rememberPhotoPicker
import com.umamit.feature.journal.R

/**
 * The session page route; its argument is the view model's `sessionId` ([SessionPageViewModel.SESSION_ID]). The
 * hero's *+ Foto* opens [photoPicker] (the system's, 06 §5.2; a fake in tests); a photo that can't be added or
 * deleted is said in a short toast (SCR-28's errors).
 */
@Composable
fun SessionPageRoute(
    host: SessionPageHost,
    modifier: Modifier = Modifier,
    viewModel: SessionPageViewModel = hiltViewModel(),
    heroViewModel: SessionHeroViewModel = hiltViewModel(),
    photoPicker: PhotoPicker = SystemPhotoPicker,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hero by heroViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SessionPageEvent.Deleted -> currentHost.onDeleted()
            }
        }
    }
    LaunchedEffect(heroViewModel) {
        heroViewModel.events.collect { event ->
            val message = when (event) {
                SessionHeroEvent.PHOTO_UNREADABLE -> R.string.session_photo_unreadable
                SessionHeroEvent.PHOTO_LIMIT_REACHED -> R.string.session_photo_limit
                SessionHeroEvent.PHOTO_FAILED -> R.string.session_photo_failed
                SessionHeroEvent.PHOTO_CHANGE_FAILED -> R.string.session_photo_change_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val actions = remember(viewModel) {
        SessionPageActions(
            back = { currentHost.onBack() },
            openTea = { currentHost.onOpenTea(it) },
            brewAgain = { currentHost.onBrewAgain(it) },
            restore = viewModel::restore,
            delete = viewModel::delete,
            edit = { currentHost.onEdit(it) },
            setRating = viewModel::setRating,
            setTaste = viewModel::setTaste,
            clearTaste = viewModel::clearTaste,
            notesChanged = viewModel::onNotesChanged,
            flushNotes = viewModel::flushNotes,
            setDescriptor = viewModel::setDescriptor,
        )
    }
    val pickPhoto = rememberPhotoPicker(photoPicker, heroViewModel::addPhoto)
    val heroActions = remember(heroViewModel, pickPhoto) {
        SessionHeroActions(addPhoto = pickPhoto, deletePhoto = heroViewModel::deletePhoto)
    }
    SessionPageScreen(
        state = state,
        modifier = modifier,
        actions = actions,
        alertKey = host.alertKey,
        hero = hero,
        heroActions = heroActions,
    )
}
