package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umamit.core.data.RecipeOfferAccept
import com.umamit.core.data.RecipeOffers
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionStatus
import com.umamit.feature.journal.RecipeOfferLine
import com.umamit.feature.journal.RecipeOfferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The text button *Zeiten ins Rezept übernehmen* under the step table while the recipe offer applies (SCR-16, PRF-07,
 * D-112); tapped, the line reads „Rezept aktualisiert." and is gone the next time the page opens.
 */
@Composable
internal fun SessionRecipeOffer(state: RecipeOfferState, onAccept: () -> Unit) {
    RecipeOfferLine(
        state = state,
        onAccept = onAccept,
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
    )
}

/**
 * The session page's recipe offer (PRF-07, D-112) for [sessionId]: the line is derived from the stored state each
 * time the session is read, until it is tapped on this page. The tap shows „Rezept aktualisiert." at once; the write
 * runs in [scope] and completes even when the page is left (rule 8); a refused write (the offer no longer applies)
 * removes the line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionOfferLine(
    private val offers: RecipeOffers,
    private val sessionId: String,
    private val scope: CoroutineScope,
) {
    private val tap = MutableStateFlow(Tap.NONE)

    /** The line for [session] as read now. */
    fun of(session: BrewSession): Flow<RecipeOfferState?> = tap.flatMapLatest { tapped ->
        when (tapped) {
            Tap.ACCEPTED -> flowOf(RecipeOfferState.ACCEPTED)

            Tap.REFUSED -> flowOf(null)

            Tap.NONE -> flow {
                val applies = session.status == SessionStatus.COMPLETED && offers.proposal(sessionId) != null
                emit(if (applies) RecipeOfferState.OFFERED else null)
            }
        }
    }

    /** *Zeiten ins Rezept übernehmen*: accepted once per page; a second tap writes nothing. */
    fun accept() {
        if (!tap.compareAndSet(Tap.NONE, Tap.ACCEPTED)) return
        scope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                if (offers.accept(sessionId) != RecipeOfferAccept.ACCEPTED) tap.value = Tap.REFUSED
            }
        }
    }

    private enum class Tap { NONE, ACCEPTED, REFUSED }
}
