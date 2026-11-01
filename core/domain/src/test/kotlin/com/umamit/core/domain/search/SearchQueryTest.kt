package com.umamit.core.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** GEN-10's in-memory search: case- and diacritics-insensitive word prefixes. */
class SearchQueryTest {
    @Test
    fun normalisingDropsCaseAndDiacritics() {
        assertThat(SearchQuery.normalized("Kōridashi Hōjicha")).isEqualTo("koridashi hojicha")
        assertThat(SearchQuery.normalized("ÜBER Café")).isEqualTo("uber cafe")
    }

    @Test
    fun everyWordOfTheQueryMustStartAWord() {
        val words = SearchQuery.words("Kabuse Sencha – Tanabata")

        assertThat(SearchQuery("tana").matches(words)).isTrue()
        assertThat(SearchQuery("SEN kab").matches(words)).isTrue()
        assertThat(SearchQuery("abata").matches(words)).isFalse()
        assertThat(SearchQuery("sencha gyokuro").matches(words)).isFalse()
    }

    @Test
    fun accentsInTheQueryOrTheTextAreIgnored() {
        assertThat(SearchQuery("hōji").matches(SearchQuery.words("Hojicha"))).isTrue()
        assertThat(SearchQuery("hoji").matches(SearchQuery.words("Hōjicha"))).isTrue()
    }

    @Test
    fun aBlankQueryIsEmptyAndMatchesEverything() {
        assertThat(SearchQuery("  ").isEmpty).isTrue()
        assertThat(SearchQuery(" – ").isEmpty).isTrue()
        assertThat(SearchQuery("").matches(emptyList())).isTrue()
        assertThat(SearchQuery("a").isEmpty).isFalse()
    }
}
