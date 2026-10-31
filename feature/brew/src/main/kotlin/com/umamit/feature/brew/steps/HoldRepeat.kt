package com.umamit.feature.brew.steps

/**
 * When a held − / + of the Schritte-Sheet repeats (SCR-31, PRF-04, D-098, #141), in ms after the press: the
 * first repeat after 400 ms, then every 150 ms; from 1,5 s every 60 ms and from 2 s every 25 ms. Each repeat
 * is one PRF-04 step, so 5:00 → 10:00 (60 steps of 5 s) takes about 3 s, and from 10 min on a repeat moves by
 * 1 min. The interval after a repeat is the one of the phase that repeat fell in.
 */
internal object HoldRepeat {
    private const val FIRST_MS = 400L
    private const val SLOW_MS = 150L
    private const val FAST_FROM_MS = 1_500L
    private const val FAST_MS = 60L
    private const val FASTEST_FROM_MS = 2_000L
    private const val FASTEST_MS = 25L

    /** The times of the repeats, in ms after the press. */
    fun timesMs(): Sequence<Long> = generateSequence(FIRST_MS) { last -> last + intervalAfter(last) }

    private fun intervalAfter(atMs: Long): Long = when {
        atMs < FAST_FROM_MS -> SLOW_MS
        atMs < FASTEST_FROM_MS -> FAST_MS
        else -> FASTEST_MS
    }
}
