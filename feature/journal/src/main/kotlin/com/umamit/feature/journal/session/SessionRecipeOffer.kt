package com.umamit.feature.journal.session

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
 * time the session or its tea is read and each time the page resumes ([refresh]), so a recipe changed on another
 * page drops it (PRF-07 AC3), until it is tapped on this page. The tap writes in [scope] and the write completes even
 * when the page is left (rule 8); the line reads „Rezept aktualisiert." once the write is done, and a refused or failed
 * write (the offer no longer applies) removes it. A failed read shows no line (D-090).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SessionOfferLine(
    private val offers: RecipeOffers,
    private val sessionId: String,
    private val scope: CoroutineScope,
) {
    private val tap = MutableStateFlow(Tap.NONE)
    private val reads = MutableStateFlow(0)

    /** The line for [session] as read now, read again on each [refresh]. */
    fun of(session: BrewSession): Flow<RecipeOfferState?> = combine(tap, reads) { tapped, _ -> tapped }
        .flatMapLatest { tapped ->
            when (tapped) {
                Tap.PENDING -> flowOf(RecipeOfferState.OFFERED)

                Tap.ACCEPTED -> flowOf(RecipeOfferState.ACCEPTED)

                Tap.REFUSED -> flowOf(null)

                Tap.NONE -> flow {
                    val applies = session.status == SessionStatus.COMPLETED && readProposal()
                    emit(if (applies) RecipeOfferState.OFFERED else null)
                }
            }
        }

    /** The page resumed: the offer is read again, as the recipe or the tea may have changed meanwhile. */
    fun refresh() {
        reads.update { it + 1 }
    }

    /** *Zeiten ins Rezept übernehmen*: accepted once per page; a second tap writes nothing. */
    fun accept() {
        if (!tap.compareAndSet(Tap.NONE, Tap.PENDING)) return
        scope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                tap.value = if (write() == RecipeOfferAccept.ACCEPTED) Tap.ACCEPTED else Tap.REFUSED
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readProposal(): Boolean = try {
        offers.proposal(sessionId) != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Recipe offer not read", e)
        false
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(): RecipeOfferAccept? = try {
        offers.accept(sessionId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Recipe offer not written", e)
        null
    }

    private enum class Tap { NONE, PENDING, ACCEPTED, REFUSED }

    private companion object {
        const val TAG = "SessionOfferLine"
    }
}
