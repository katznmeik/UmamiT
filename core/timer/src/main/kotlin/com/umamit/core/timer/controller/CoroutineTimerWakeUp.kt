package com.umamit.core.timer.controller

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The in-process wake-up on a coroutine (04 §4, §6.1). It runs while the
 * process lives; in deep sleep the exact alarms wake the phone instead.
 *
 * A wake-up that has fired is detached before its callback runs, so the
 * controller can re-arm or cancel from inside that callback without
 * cancelling the lock turn the wake-up started. A callback that throws is
 * logged; later wake-ups still run, and the alarms still deliver the event.
 */
@Singleton
class CoroutineTimerWakeUp internal constructor(private val scope: CoroutineScope) : TimerWakeUpPort {

    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val guard = Any()

    /** The armed wake-up, until it fires or is replaced; guarded by [guard]. */
    private var armed: Job? = null

    override fun arm(delayMs: Long, onDue: suspend () -> Unit) {
        synchronized(guard) {
            armed?.cancel()
            armed = scope.launch {
                delay(delayMs)
                if (detach(coroutineContext[Job])) fire(onDue)
            }
        }
    }

    override fun cancel() {
        synchronized(guard) {
            armed?.cancel()
            armed = null
        }
    }

    /** True when [job] is still the armed wake-up; it is then no longer armed and cannot be cancelled by [arm]. */
    private fun detach(job: Job?): Boolean = synchronized(guard) {
        (armed === job).also { if (it) armed = null }
    }

    // A failed wake-up is logged; the exact alarms still deliver (04 §6).
    private suspend fun fire(onDue: suspend () -> Unit) {
        attempt { onDue() }?.let { e -> Log.e("UmamiTTimer", "wake-up failed", e) }
    }
}
