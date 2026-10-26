package com.umamit.core.data.catalog

import com.umamit.core.common.WallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/** Where the catalog refresh is, for the refresh sheet (SCR-22). */
sealed interface CatalogRefreshState {
    /** No refresh running and no result to show. */
    data object Idle : CatalogRefreshState

    data class Running(val progress: CatalogRefreshProgress) : CatalogRefreshState

    /** The last refresh's result, until [CatalogRefreshRunner.acknowledge]. */
    data class Finished(val outcome: CatalogRefreshOutcome) : CatalogRefreshState
}

/**
 * Starts, cancels and reports the catalog refresh (CAT-07): only on the user's tap (AC7), one at
 * a time. The screens see only this; how the refresh is hosted is the implementation's business.
 */
interface CatalogRefreshRunner {
    val state: StateFlow<CatalogRefreshState>

    /** Starts a refresh unless one is running or a cancelled one is still cleaning up. */
    fun start()

    /**
     * *Abbrechen*: cancels the running refresh, which ends as [CatalogRefreshOutcome.Cancelled], or
     * as the success it is once its swap has begun (D-090).
     */
    fun cancel()

    /** The result was seen: [CatalogRefreshState.Finished] becomes [CatalogRefreshState.Idle]. */
    fun acknowledge()
}

/**
 * The refresh job (#64, #65): the Android-independent [CatalogRefresher] runs in the app's own
 * scope, independent of any screen, and the `dataSync` foreground service ([CatalogRefreshService],
 * through [CatalogRefreshForeground]) keeps the process running while it lasts, so the phone may
 * be locked (CAT-07 AC1, 06 §5.3). The service follows this runner's [state] for its progress
 * notification and cancels through it ([CatalogRefreshServicePort]); it never runs or ends the
 * refresh itself.
 *
 * The timer is left alone (CAT-07 AC8, rule 8): an own scope, no alarms, no shared locks, and
 * nothing the refresh throws escapes into the process the timer runs in: an error the refresher
 * does not turn into an outcome itself ends the run as [CatalogRefreshOutcome.ShopChanged].
 */
@Singleton
class AppCatalogRefreshRunner internal constructor(
    private val refresh: suspend (onProgress: (CatalogRefreshProgress) -> Unit) -> CatalogRefreshOutcome,
    private val foreground: CatalogRefreshForeground,
    private val diagnostics: DiagnosticsLog,
    private val clock: WallClock,
    context: CoroutineContext,
) : CatalogRefreshRunner,
    CatalogRefreshServicePort {
    @Inject
    constructor(
        refresher: CatalogRefresher,
        foreground: CatalogRefreshForeground,
        diagnostics: DiagnosticsLog,
        clock: WallClock,
    ) : this(refresher::refresh, foreground, diagnostics, clock, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + context)

    private val mutableState = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)
    override val state: StateFlow<CatalogRefreshState> = mutableState.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    override fun start() {
        synchronized(lock) {
            // Not while a cancelled run still cleans up (staging, log, photos): runs never overlap (D-090).
            if (job?.isCompleted == false) return
            // A new run: an earlier result's notification goes.
            foreground.clearResult()
            mutableState.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
            val run = scope.launch {
                val outcome = runGuarded()
                mutableState.value = CatalogRefreshState.Finished(outcome)
            }
            job = run
            // Also when cancelled before it ran: the refresher has logged and cleaned up what it started.
            // Only while it is still the current run, so an ended run never overwrites the next one's state.
            // A run that ended itself (a success whose swap was in when the cancel came, D-090) keeps its result.
            run.invokeOnCompletion { cause ->
                synchronized(lock) {
                    if (cause != null && job === run && mutableState.value !is CatalogRefreshState.Finished) {
                        mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Cancelled)
                    }
                }
            }
        }
        // The state is Running before the service's first command reads it.
        foreground.start()
    }

    /** The refresh; an error it lets through (a stack overflow in a page, …) ends the run instead of the process. */
    private suspend fun runGuarded(): CatalogRefreshOutcome = try {
        refresh { mutableState.value = CatalogRefreshState.Running(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Rule 8: the timer runs in this process; nothing of the refresh may take it down.
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        val name = e.javaClass.simpleName
        log("catalog refresh: unexpected $name, ended as shop changed")
        CatalogRefreshOutcome.ShopChanged("unexpected error: $name")
    }

    override fun cancel() {
        synchronized(lock) { job?.cancel() }
    }

    override fun acknowledge() {
        mutableState.update { if (it is CatalogRefreshState.Finished) CatalogRefreshState.Idle else it }
        foreground.clearResult()
    }

    override fun log(message: String) = diagnostics.append(clock.nowMs(), clock.zone(), message)
}
