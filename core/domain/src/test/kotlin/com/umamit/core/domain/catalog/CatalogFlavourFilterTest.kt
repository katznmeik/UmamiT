package com.umamit.core.domain.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogTeaSummary
import com.umamit.core.model.TasteNoteOption
import com.umamit.core.model.TasteScale
import com.umamit.core.model.TasteScaleOption
import org.junit.Test

/**
 * The flavour filter (v0.5.0, #204, D-108 point 8, CAT-10): the shop's taste notes (*Geschmack*) and taste
 * scales in the filter sheet. Invented teas (D-016).
 */
class CatalogFlavourFilterTest {
    private fun tea(
        key: String,
        notes: List<String> = emptyList(),
        scales: List<TasteScale> = emptyList(),
        kind: CatalogKind = CatalogKind.GREEN,
    ) = CatalogTeaSummary(
        key = key,
        name = "Tee $key",
        secondaryName = null,
        teaType = "Sorte",
        basePriceCentsPer100g = 1000,
        available = true,
        tasteNotes = notes,
        tasteScales = scales,
        kind = kind,
    )

    private val teas = listOf(
        tea("G1", listOf("fruchtig", "mild", "süß")),
        tea("G2", listOf("Mild", "Süss")),
        tea("G3", listOf("mild", "blumig")),
        tea("G4", listOf("Süß", "fruchtig")),
        tea("B1", listOf("malzig", "süß"), kind = CatalogKind.BLACK),
        tea("B2", listOf("malzig", "würzig"), kind = CatalogKind.BLACK),
    )

    private fun list(filter: CatalogFilter, hits: Set<String>? = null, of: List<CatalogTeaSummary> = teas) =
        CatalogBrowser.listing(of, filter, hits, null)

    @Test
    fun theViewOffersItsKindsNotesMostFrequentFirst() {
        // „süß" and „Süß" are one note (normalised like search, GEN-10), „Süss" another word; ties by name.
        assertThat(list(CatalogFilter(kind = CatalogKind.GREEN)).tasteNotes).containsExactly(
            TasteNoteOption("mild", "mild", 3),
            TasteNoteOption("fruchtig", "fruchtig", 2),
            TasteNoteOption("suß", "süß", 2),
            TasteNoteOption("blumig", "blumig", 1),
            TasteNoteOption("suss", "süss", 1),
        ).inOrder()
        assertThat(list(CatalogFilter(kind = CatalogKind.BLACK)).tasteNotes.map { it.label })
            .containsExactly("malzig", "süß", "würzig").inOrder()
    }

    @Test
    fun aTeaMatchesWhenItNamesEveryChosenNote() {
        assertThat(
            list(CatalogFilter(kind = CatalogKind.GREEN, notes = setOf("mild"))).teas.map {
                it.key
            },
        ).containsExactly("G1", "G2", "G3")
        assertThat(
            list(CatalogFilter(kind = CatalogKind.GREEN, notes = setOf("mild", "suß"))).teas.map {
                it.key
            },
        ).containsExactly("G1")
        // Whole notes only: „mild" is no prefix match of „mildsüß", nor „süß" of „Süss".
        val partial = listOf(tea("P1", listOf("mildsüß")), tea("P2", listOf("Süss")))
        assertThat(list(CatalogFilter(kind = CatalogKind.GREEN, notes = setOf("mild")), of = partial).teas).isEmpty()
        assertThat(list(CatalogFilter(kind = CatalogKind.GREEN, notes = setOf("suß")), of = partial).teas).isEmpty()
    }

    private val scaled = listOf(
        tea("S1", scales = listOf(TasteScale("Umami", 4), TasteScale("Süße", 3), TasteScale("Bitterstoffe", 1))),
        tea("S2", scales = listOf(TasteScale("Süße", 2), TasteScale("Bitterstoffe", 4))),
        tea("S3", scales = listOf(TasteScale("Nussig", 5))),
        tea("S4"),
        tea("SB", scales = listOf(TasteScale("Würzig", 3)), kind = CatalogKind.BLACK),
    )

    @Test
    fun theViewOffersTheScalesItsRangeHasButUmami() {
        // Umami stays its own filter; the others by how many teas show them, ties by name.
        assertThat(list(CatalogFilter(kind = CatalogKind.GREEN), of = scaled).tasteScales).containsExactly(
            TasteScaleOption("bitterstoffe", "Bitterstoffe"),
            TasteScaleOption("suße", "Süße"),
            TasteScaleOption("nussig", "Nussig"),
        ).inOrder()
        assertThat(list(CatalogFilter(kind = CatalogKind.BLACK), of = scaled).tasteScales.map { it.label })
            .containsExactly("Würzig")
    }

    @Test
    fun aScaleMinimumKeepsTheTeasThatReachIt() {
        fun shown(minimums: Map<String, Int>) =
            list(CatalogFilter(kind = CatalogKind.GREEN, minScales = minimums), of = scaled).teas.map { it.key }
        assertThat(shown(mapOf("suße" to 2))).containsExactly("S1", "S2")
        assertThat(shown(mapOf("suße" to 3))).containsExactly("S1")
        // Every minimum must be reached; a tea without the scale never does.
        assertThat(shown(mapOf("suße" to 2, "bitterstoffe" to 2))).containsExactly("S2")
        assertThat(shown(mapOf("nussig" to 1))).containsExactly("S3")
        assertThat(shown(emptyMap())).containsExactly("S1", "S2", "S3", "S4")
    }

    @Test
    fun notesAndScalesCountInTheFilterButtonAndResetClearsThem() {
        assertThat(CatalogFilter(notes = setOf("mild", "suß")).changedFilters).isEqualTo(1)
        assertThat(CatalogFilter(minScales = mapOf("suße" to 2)).changedFilters).isEqualTo(1)
        assertThat(CatalogFilter(minScales = mapOf("suße" to 2, "nussig" to 1)).changedFilters).isEqualTo(2)
        // They apply and count in every kind's view, unlike „Pur" and Umami.
        val black = CatalogFilter(kind = CatalogKind.BLACK, notes = setOf("malzig"), minScales = mapOf("wurzig" to 3))
        // The kind itself doesn't count (D-114).
        assertThat(black.changedFilters).isEqualTo(1 + 1)
        assertThat(black.withoutFilters().notes).isEmpty()
        assertThat(black.withoutFilters().minScales).isEmpty()
    }

    @Test
    fun searchHitsHiddenByTheFlavourFilterStillCount() {
        val listing =
            list(
                CatalogFilter(kind = CatalogKind.GREEN, search = "x", notes = setOf("blumig")),
                hits = setOf("G1", "G3", "B1"),
            )
        assertThat(listing.teas.map { it.key }).containsExactly("G3")
        assertThat(listing.hiddenMatches).isEqualTo(1 + 1)
        assertThat(listing.hiddenInOtherKinds).containsExactly(CatalogKind.BLACK, 1)
    }

    @Test
    fun aKindChangeKeepsOnlyTheNotesAndScalesTheNewKindHas() {
        val all = teas + scaled
        val green = CatalogFilter(
            kind = CatalogKind.GREEN,
            notes = setOf("suß", "mild"),
            minScales = mapOf(
                "suße" to 2,
                "wurzig" to 1,
            ),
        )
        val listing = list(green, of = all)
        val black = green.withKind(CatalogKind.BLACK).keepingFlavoursOf(listing)
        assertThat(black.notes).containsExactly("suß")
        assertThat(black.minScales).containsExactly("wurzig", 1)
        // And back: the green notes the black view dropped stay dropped.
        assertThat(black.withKind(CatalogKind.GREEN).keepingFlavoursOf(listing).notes).containsExactly("suß")
    }

    @Test
    fun theMarkedViewAppliesOnlyTheSearch() {
        val marked = teas.map { it.copy(marked = true) }
        assertThat(list(CatalogFilter(marked = true, notes = setOf("blumig")), of = marked).teas).hasSize(teas.size)
    }
}
