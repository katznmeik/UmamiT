package com.umamit.feature.journal.session

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.DescriptorGroup
import com.umamit.core.model.TasteDescriptor
import org.junit.Test

/** The descriptor sheet's search (SCR-30, GEN-10): pure, over German labels as the strings give them. */
class DescriptorSearchTest {
    private val labels = mapOf(
        TasteDescriptor.MARINE_NORI to "Nori",
        TasteDescriptor.MARINE_ALGEN to "Algen",
        TasteDescriptor.ROASTED_NUTTY_GEROESTETE_NUESSE to "Geröstete Nüsse",
        TasteDescriptor.MOUTHFEEL_CREMIG to "Cremig",
    )

    private fun search(query: String) = matchingGroups(query) { labels[it] ?: it.key }
        .flatMap { (_, members) -> members }
        .filter { it in labels }

    @Test
    fun `an empty query keeps every group in the vocabulary order`() {
        val groups = matchingGroups("  ") { labels[it] ?: it.key }

        assertThat(groups.map { it.first }).containsExactlyElementsIn(DescriptorGroup.entries).inOrder()
    }

    @Test
    fun `nor finds Nori and nothing else of the four`() {
        assertThat(search("nor")).containsExactly(TasteDescriptor.MARINE_NORI)
    }

    @Test
    fun `case and accents are ignored`() {
        assertThat(search("NUSSE")).containsExactly(TasteDescriptor.ROASTED_NUTTY_GEROESTETE_NUESSE)
        assertThat(search("gerostete")).containsExactly(TasteDescriptor.ROASTED_NUTTY_GEROESTETE_NUESSE)
    }

    @Test
    fun `a group without a match is left out`() {
        val groups = matchingGroups("nori") { labels[it] ?: it.key }

        assertThat(groups.map { it.first }).containsExactly(DescriptorGroup.MARINE)
    }

    @Test
    fun `nothing matches nonsense`() {
        assertThat(matchingGroups("zzzz") { labels[it] ?: it.key }).isEmpty()
    }
}
