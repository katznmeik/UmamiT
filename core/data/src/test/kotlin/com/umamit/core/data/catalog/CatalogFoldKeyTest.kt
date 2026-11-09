package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogKind
import org.junit.Test

/** The folded-group keys per catalog kind (#199, D-108): the v0.4.1 entries of `catalogFolded` read back unchanged. */
class CatalogFoldKeyTest {
    @Test
    fun theKeysOfTheFirstTwoKindsAreThoseOfV041() {
        assertThat(CatalogFoldKey.of(CatalogKind.GREEN, "Sencha")).isEqualTo("green/Sencha")
        assertThat(CatalogFoldKey.of(CatalogKind.GREEN, null)).isEqualTo("green/-")
        assertThat(CatalogFoldKey.of(CatalogKind.HERBAL, "Mischungen")).isEqualTo("herbal/Mischungen")
        assertThat(CatalogFoldKey.of(CatalogKind.HERBAL, null)).isEqualTo("herbal/-")
        assertThat(CatalogFoldKey.prefix(CatalogKind.GREEN)).isEqualTo("green/")
        assertThat(CatalogFoldKey.prefix(CatalogKind.HERBAL)).isEqualTo("herbal/")
    }

    @Test
    fun aStoredV041SetFoldsTheSameGroupsPerKind() {
        val stored = setOf("green/Sencha", "green/-", "herbal/Mischungen")

        fun foldedIn(kind: CatalogKind) = stored.filter { it.startsWith(CatalogFoldKey.prefix(kind)) }
        assertThat(foldedIn(CatalogKind.GREEN)).containsExactly("green/Sencha", "green/-")
        assertThat(foldedIn(CatalogKind.HERBAL)).containsExactly("herbal/Mischungen")
    }

    @Test
    fun aThirdKindGetsItsOwnKeysWithoutTouchingTheOthers() {
        val oolong = CatalogKind("oolong", setOf("oolong"))
        assertThat(CatalogFoldKey.of(oolong, "Sencha")).isEqualTo("oolong/Sencha")
        assertThat(CatalogFoldKey.prefix(oolong)).isEqualTo("oolong/")
        assertThat(CatalogFoldKey.of(oolong, "Sencha")).isNotEqualTo(CatalogFoldKey.of(CatalogKind.GREEN, "Sencha"))
    }
}
