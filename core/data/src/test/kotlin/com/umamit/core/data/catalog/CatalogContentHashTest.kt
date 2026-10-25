package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When a later refresh reports a tea as „geändert" (05 §4.1 `content_hash`, D-086). */
class CatalogContentHashTest {
    @Test
    fun aDifferentHashUnderTheSameParserVersionIsAChange() {
        assertThat(CatalogContentHash.changed("4:aaaa", "4:bbbb")).isTrue()
        assertThat(CatalogContentHash.changed("4:aaaa", "4:aaaa")).isFalse()
    }

    @Test
    fun aNewParserOrAMissingHashIsNoChangeOfTheShop() {
        // An app update reads the same page differently; a row from before #70 has no hash.
        assertThat(CatalogContentHash.changed("4:aaaa", "5:bbbb")).isFalse()
        assertThat(CatalogContentHash.changed(null, "4:bbbb")).isFalse()
    }
}
