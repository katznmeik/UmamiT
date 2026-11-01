package com.umamit.core.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * GEN-10's instant local search, shared by the tea picker (SCR-03) and *Meine Tees* (TEA-09, D-100):
 * case- and diacritics-insensitive word prefixes. Every word of the query must start a word of the
 * searched text; a query without words matches everything.
 *
 * @param text the search text as typed.
 */
class SearchQuery(text: String) {
    /** The query's words, [normalized]. */
    val parts: List<String> = words(text)

    /** The query has no word: no search. */
    val isEmpty: Boolean get() = parts.isEmpty()

    /** Every part starts one of [words] (already [normalized], e.g. from [SearchQuery.words]). */
    fun matches(words: Collection<String>): Boolean = parts.all { part -> words.any { it.startsWith(part) } }

    companion object {
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val WORD_BREAK = Regex("[\\s\\-–/·,()]+")

        /** Lower case without diacritics: „Kōridashi" → „koridashi" (GEN-10). */
        fun normalized(text: String): String =
            COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.GERMAN)

        /** The [normalized] words of [text], split at spaces, dashes, slashes, commas and brackets. */
        fun words(text: String): List<String> = normalized(text).split(WORD_BREAK).filter { it.isNotEmpty() }
    }
}
