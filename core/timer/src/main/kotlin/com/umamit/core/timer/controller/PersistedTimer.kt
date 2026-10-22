package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.SessionRepository
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerSnapshot
import com.umamit.core.timer.engine.TimerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * What a new process finds of the timer (04 §4, §7.2; D-057): the persisted
 * state the [BrewSessionController]'s first turn recovers, with its tea for
 * the UI. Read inside the controller's event lock.
 */
internal class PersistedTimer(
    private val activeBrew: ActiveBrewStore,
    private val sessions: SessionRepository,
    private val effects: TransitionEffects,
) {
    /** The restored state ([TimerState.Idle] for none) and the tea of its brew session. */
    data class Restored(val state: TimerState, val teaName: String?)

    /**
     * The persisted state at [now]. A snapshot `restore` refuses is cleared
     * and logged and counts as none, so no wrong timer runs. A failed read is
     * logged and tried again after short pauses ([READ_RETRY_PAUSES_MS]); null
     * when every try failed, so the controller makes the turn come again and a
     * passing database error never loses the session (rule 8, D-057).
     */
    suspend fun read(now: ClockReading): Restored? {
        val snapshot = readWithRetries(now) ?: return null
        val state = try {
            BrewTimerEngine.restore(snapshot.value)
        } catch (e: IllegalArgumentException) {
            effects.record(TimerDiagnostic.SnapshotUnreadable(now, e))
            effects.discardUnreadable(now)
            TimerState.Idle
        }
        return Restored(state, teaNameOf(state, now))
    }

    private suspend fun readWithRetries(now: ClockReading): Read<TimerSnapshot?>? {
        var read = guarded(now) { activeBrew.read() }
        val pauses = READ_RETRY_PAUSES_MS.iterator()
        while (read == null && pauses.hasNext()) {
            delay(pauses.next())
            read = guarded(now) { activeBrew.read() }
        }
        return read
    }

    /** The tea of a brew session, from its session row; null for the quick timer, idle or an unreadable row. */
    private suspend fun teaNameOf(state: TimerState, now: ClockReading): String? {
        val sessionId = ((state as? TimerState.Active)?.session?.kind as? SessionKind.Brew)?.sessionId
        return sessionId?.let { id -> guarded(now) { sessions.getSession(id)?.snapshot?.teaName }?.value }
    }

    /** Runs [read]; a failure of any kind is logged as [TimerDiagnostic.RecoveryFailed] and gives null. */
    @Suppress("TooGenericExceptionCaught") // a storage failure of any kind is logged, never fatal (rule 8)
    private suspend fun <T> guarded(now: ClockReading, read: suspend () -> T): Read<T>? = try {
        Read(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        effects.record(TimerDiagnostic.RecoveryFailed(now, e))
        null
    }

    /** A value that was read, null or not. */
    private class Read<T>(val value: T)

    private companion object {
        /** Pauses before the second and third read; the turn of an alarm stays awake meanwhile (`goAsync`). */
        val READ_RETRY_PAUSES_MS = listOf(250L, 1_000L)
    }
}
