package com.umamit.core.data.catalog

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

    /** Starts a refresh unless one is running. */
    fun start()

    /** *Abbrechen*: cancels the running refresh, which ends as [CatalogRefreshOutcome.Cancelled]. */
    fun cancel()

    /** The result was seen: [CatalogRefreshState.Finished] becomes [CatalogRefreshState.Idle]. */
    fun acknowledge()
}

/**
 * The refresh as an app-scoped job (#64): it runs while the app's process lives, independent of
 * any screen, and leaves the timer alone (CAT-07 AC8: own scope, no alarms, no shared locks).
 * #65 hosts the same [CatalogRefresher] in the `dataSync` foreground service, so it survives a
 * locked phone (06 §5.3).
 */
@Singleton
class AppCatalogRefreshRunner internal constructor(
    private val refresher: CatalogRefresher,
    private val scope: CoroutineScope,
) : CatalogRefreshRunner {
    @Inject
    constructor(refresher: CatalogRefresher) : this(refresher, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val mutableState = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)
    override val state: StateFlow<CatalogRefreshState> = mutableState.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    override fun start() {
        synchronized(lock) {
            if (job?.isActive == true) return
            mutableState.value = CatalogRefreshState.Running(CatalogRefreshProgress.STARTING)
            job = scope.launch {
                val outcome = refresher.refresh { mutableState.value = CatalogRefreshState.Running(it) }
                mutableState.value = CatalogRefreshState.Finished(outcome)
            }.apply {
                // Also when cancelled before it ran: the refresher has logged and cleaned up what it started.
                invokeOnCompletion { cause ->
                    if (cause != null) {
                        mutableState.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Cancelled)
                    }
                }
            }
        }
    }

    override fun cancel() {
        synchronized(lock) { job?.cancel() }
    }

    override fun acknowledge() {
        mutableState.update { if (it is CatalogRefreshState.Finished) CatalogRefreshState.Idle else it }
    }
}
