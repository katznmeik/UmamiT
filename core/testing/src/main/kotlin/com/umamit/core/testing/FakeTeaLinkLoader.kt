package com.umamit.core.testing

import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.core.data.catalog.TeaLinkOutcome
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [TeaLinkLoader] (CAT-17): no shop. A text whose only word starts with the shop's
 * `https://www.tee-kontor-kiel.de/` is a link; every [load] is recorded in [loaded] and answers
 * [outcome] for its text, after [hold] completes when one is set (to see the loading state).
 */
class FakeTeaLinkLoader(var outcome: (String) -> TeaLinkOutcome = { TeaLinkOutcome.NotATeaLink }) : TeaLinkLoader {
    val loaded = mutableListOf<String>()

    /** When set, each [load] waits for it before it answers. */
    var hold: CompletableDeferred<Unit>? = null

    override fun linkIn(text: String): String? =
        text.trim().takeIf { it.startsWith(SHOP) && it.none(Char::isWhitespace) }

    override suspend fun load(text: String): TeaLinkOutcome {
        loaded += text
        hold?.await()
        return outcome(text)
    }

    private companion object {
        const val SHOP = "https://www.tee-kontor-kiel.de/"
    }
}
