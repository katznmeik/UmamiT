package com.umamit.core.timer.controller

import com.umamit.core.common.ClockReading
import com.umamit.core.data.ActiveBrewStore
import com.umamit.core.data.SessionRepository
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEffect
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
    private val alarms: TimerAlarmPort,
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
            discardUnreadable(now)
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

    /**
     * The persisted state cannot be restored (04 §7.2): its row is cleared and its alarms
     * cancelled, so nothing runs a wrong timer; each step is logged if it fails.
     */
    suspend fun discardUnreadable(now: ClockReading) {
        attempt { activeBrew.clear() }
            ?.let { effects.record(TimerDiagnostic.PersistFailed(now, retry = false, error = it)) }
        attempt { alarms.reconcile(DueTimes.NONE) }
            ?.let { effects.record(TimerDiagnostic.EffectFailed(now, TimerEffect.ScheduleAlarms(DueTimes.NONE), it)) }
    }

    /**
     * Ends every session still in progress other than [activeSessionId] (04 §7.2, E11): a
     * session whose `active_brew` row is gone, after a device restore or because its stored
     * timer was unreadable, would otherwise stay in progress forever. It is saved when it has a
     * completed infusion (a non-rinse step ended and not skipped), as the app's own finish
     * (`auto_finished`), else discarded; nothing resumes. A failure is logged; the next process
     * start tries again.
     */
    @Suppress("TooGenericExceptionCaught") // a storage failure of any kind is logged, never fatal (rule 8)
    suspend fun endOrphans(activeSessionId: String?, now: ClockReading) {
        try {
            sessions.inProgressSessionIds().filter { it != activeSessionId }.forEach { id ->
                val session = sessions.getSession(id) ?: return@forEach
                val saved = session.steps.any { it.kind != StepKind.RINSE && it.actualSeconds != null && !it.skipped }
                if (saved) sessions.completeSession(id, autoFinished = true) else sessions.discardSession(id)
                effects.record(TimerDiagnostic.OrphanSessionEnded(now, saved))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            effects.record(TimerDiagnostic.OrphanSessionsFailed(now, e))
        }
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

/**
 * When a turn that could not read the persisted timer comes again (D-057, D-063): every 5 s
 * for the first 30 s of failed reads, every 30 s up to 5 min, then every 2 min. After 11 min,
 * when the 10-min overtime window has surely passed, recovery gives up: a recovered step would
 * only be acknowledged silently by then (04 §7.2).
 */
internal object RecoveryBackoff {
    private const val SECOND_MS = 1_000L
    private const val MINUTE_MS = 60 * SECOND_MS
    private const val FAST_UNTIL_MS = 30 * SECOND_MS
    private const val FAST_DELAY_MS = 5 * SECOND_MS
    private const val MEDIUM_UNTIL_MS = 5 * MINUTE_MS
    private const val MEDIUM_DELAY_MS = 30 * SECOND_MS
    private const val GIVE_UP_AFTER_MS = 11 * MINUTE_MS
    private const val SLOW_DELAY_MS = 2 * MINUTE_MS

    /** The delay before the next try after the reads have failed for [failingForMs]; null: give up. */
    fun nextDelayMs(failingForMs: Long): Long? = when {
        failingForMs < FAST_UNTIL_MS -> FAST_DELAY_MS
        failingForMs < MEDIUM_UNTIL_MS -> MEDIUM_DELAY_MS
        failingForMs < GIVE_UP_AFTER_MS -> SLOW_DELAY_MS
        else -> null
    }
}
