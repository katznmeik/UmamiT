package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.umamit.core.common.AppLanguage
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.TastingLimits
import com.umamit.feature.journal.R
import com.umamit.feature.journal.RatingRows

const val SESSION_NOTES_TAG = "session_notes"

/** The notes counter shows from this length on (SCR-16). */
private const val NOTES_COUNTER_FROM = 4_500
private const val NOTES_MIN_LINES = 3

/**
 * The two quick tasting inputs of the session page (SCR-16, D-112), above the step table: BEWERTUNG (the stars,
 * JRN-02) and NOTIZEN (JRN-05). Both are optional and save as they change; nothing marks them as missing.
 */
@Composable
internal fun TastingInputs(page: SessionPage, actions: SessionPageActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        RatingSection(page, actions)
        NotesSection(page, actions)
    }
}

/**
 * BEWERTUNG (JRN-02, D-117): the two star rows, „Tee" above „Zubereitung", empty until tapped; each shown value
 * follows its own tap before the write returns.
 */
@Composable
private fun RatingSection(page: SessionPage, actions: SessionPageActions) {
    // Each row keeps the rating as its last tap said it, until the repository shows exactly that: a stale emission
    // still carrying an older value (a later write is queued) must not flick the stars back. The rows are independent.
    var teaPending by remember(page.sessionId) { mutableStateOf<PendingRating?>(null) }
    var brewingPending by remember(page.sessionId) { mutableStateOf<PendingRating?>(null) }
    LaunchedEffect(page.rating, teaPending) { if (teaPending?.value == page.rating) teaPending = null }
    LaunchedEffect(page.brewingRating, brewingPending) {
        if (brewingPending?.value == page.brewingRating) brewingPending = null
    }
    val teaTapped = teaPending
    val brewingTapped = brewingPending
    Column {
        SectionHeader(
            title = stringResource(R.string.session_rating),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        RatingRows(
            tea = if (teaTapped != null) teaTapped.value else page.rating,
            brewing = if (brewingTapped != null) brewingTapped.value else page.brewingRating,
            onTea = {
                teaPending = PendingRating(it)
                actions.setRating(it)
            },
            onBrewing = {
                brewingPending = PendingRating(it)
                actions.setBrewingRating(it)
            },
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        )
    }
}

/** A tapped rating not yet seen in the store; null inside is „no rating" (the third tap on a star). */
private data class PendingRating(val value: Double?)

/**
 * NOTIZEN (JRN-05): a multi-line field that grows with its text, at most 5000 characters with a counter from 4500.
 * Saved by the view model 1 s after the last key; the field losing focus, the page being left (its composition
 * ends: back, a tab switch, an alert's timer on top) or the app stopping saves at once.
 */
@Composable
private fun NotesSection(page: SessionPage, actions: SessionPageActions) {
    var text by rememberSaveable(page.sessionId) { mutableStateOf(page.notes.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    val flush by rememberUpdatedState(actions.flushNotes)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flush() }
    DisposableEffect(Unit) { onDispose { flush() } }
    Column {
        SectionHeader(
            title = stringResource(R.string.session_notes),
            modifier = Modifier.padding(top = UmamiSpacing.Medium),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                val capped = it.take(TastingLimits.MAX_NOTES_LENGTH)
                text = capped
                actions.notesChanged(capped)
            },
            placeholder = { Text(stringResource(R.string.session_notes_placeholder)) },
            minLines = NOTES_MIN_LINES,
            supportingText = if (text.length >= NOTES_COUNTER_FROM) {
                {
                    Text(
                        stringResource(
                            R.string.session_notes_counter,
                            grouped(text.length),
                            grouped(TastingLimits.MAX_NOTES_LENGTH),
                        ),
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
                .onFocusChanged {
                    if (focused && !it.isFocused) flush()
                    focused = it.isFocused
                }
                .testTag(SESSION_NOTES_TAG),
        )
    }
}

/** „4.501" (English „4,501"): a count in the app's language (rule 4, GEN-13). */
internal fun grouped(count: Int): String = "%,d".format(AppLanguage.current().locale, count)
