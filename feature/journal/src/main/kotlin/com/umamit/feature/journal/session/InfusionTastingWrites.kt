package com.umamit.feature.journal.session

import com.umamit.core.data.SessionTasting
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TastingLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The session page's tasting writes per infusion (JRN-03, JRN-05, D-114), addressed by the step's `position`. They run
 * one after another in release order, so the last one is the one stored, and each completes even when the page is
 * left meanwhile or an alert takes the screen (rule 8). They touch only the step's taste and note columns (05 §3.7).
 */
internal class InfusionTastingWrites(
    private val tasting: SessionTasting,
    private val sessionId: String,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    /** A slider of the infusion at [position] released on [value]: saved at once, after any earlier write. */
    fun setTaste(position: Int, axis: TasteAxis, value: Int) {
        write { tasting.setInfusionTaste(sessionId, position, axis, value) }
    }

    /** *Zurücksetzen*: the six taste values of the infusion at [position] are unset; its note stays. */
    fun clearTaste(position: Int) {
        write { tasting.clearInfusionTaste(sessionId, position) }
    }

    /**
     * The note of the infusion at [position], as the shared editor hands it over (1 s after the last key, or when the
     * field, the fold or the page is left): trimmed and stored by the repository, blank as none.
     */
    fun setNote(position: Int, note: String) {
        val text = note.take(TastingLimits.MAX_INFUSION_NOTE_LENGTH)
        write { tasting.setInfusionNote(sessionId, position, text) }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.ATOMIC) { withContext(NonCancellable) { lock.withLock { block() } } }
    }
}
