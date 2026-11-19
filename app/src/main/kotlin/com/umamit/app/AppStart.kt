package com.umamit.app

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The guards of the process start (D-133 item 13): nothing the start runs besides the timer's own recovery may crash
 * the process, so a start that an alarm made always reaches the timer (rule 8).
 */
internal object AppStart {
    private const val TAG = "UmamiTStart"

    /**
     * The replacing import's start gate (05 §8 crash safety): [recover] when [isNeeded], before anything that follows
     * reads the database, the photos or the settings. False from [recover], or a throw (the graph's `get()`
     * included), is logged and the start goes on: the marker stays and the next try or start finishes it.
     */
    fun importGate(isNeeded: () -> Boolean, recover: () -> Boolean) {
        try {
            if (isNeeded() && !recover()) Log.w(TAG, "import not finished at start; the next try finishes it")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // the graph, IO, SQLite: never a crash loop
        ) {
            Log.w(TAG, "import gate failed; the next start tries again", e)
        }
    }

    /**
     * [block] launched in [scope], its failure logged instead of reaching the thread's handler (which ends the
     * process); a cancellation still cancels. For the start-up work other than the timer's recovery.
     */
    fun CoroutineScope.launchGuarded(
        name: String,
        dispatcher: CoroutineDispatcher? = null,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = launch(dispatcher ?: EmptyCoroutineContext) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // any start-up task: logged, the app goes on
        ) {
            Log.w(TAG, "start-up task $name failed", e)
        }
    }
}
