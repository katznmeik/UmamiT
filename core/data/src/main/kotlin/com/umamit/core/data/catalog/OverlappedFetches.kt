package com.umamit.core.data.catalog

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Fetches [items] in order, one after another, while [process] works on the previous answer
 * (D-091, NFR-PERF-11): the next request waits for the rate limit's gap anyway, so parsing a
 * page, re-encoding a photo or writing it happens in that gap instead of after it.
 *
 * - **The wire is untouched** (CLAUDE.md rule 7): [fetch] is called for one item at a time, in
 *   order, and every request still goes through the one session (one connection, ≤ 2 requests/s,
 *   robots.txt, stops).
 * - **Bounded:** a rendezvous hands each answer over; while [process] works on one answer, at
 *   most the next one is fetched and waits. So at most two answers are held at once.
 * - **Stops:** after an answer [isLast] calls the last one (a stop), nothing more is fetched; it
 *   still reaches [process], in order, which ends the run. The session's stop is sticky anyway.
 * - **Cancellation and failures:** [process] runs in the caller's coroutine. When it throws, or
 *   the caller is cancelled, the request in flight is cancelled and nothing more is sent; the
 *   answer already fetched for the next item is dropped.
 */
internal suspend fun <T, A> fetchOverlapped(
    items: List<T>,
    fetch: suspend (T) -> A,
    isLast: (A) -> Boolean,
    process: suspend (T, A) -> Unit,
) {
    if (items.isEmpty()) return
    coroutineScope {
        val answers = Channel<Pair<T, A>>(Channel.RENDEZVOUS)
        launch {
            try {
                for (item in items) {
                    ensureActive()
                    val answer = fetch(item)
                    answers.send(item to answer)
                    if (isLast(answer)) break
                }
            } finally {
                answers.close()
            }
        }
        for ((item, answer) in answers) process(item, answer)
    }
}
