package com.umamit.feature.collection.editor

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TeaCategory
import org.junit.Test

/** *Sorte wählen*'s list (SCR-11, TEA-01, D-100): every built-in style by Kategorie, searchable. */
class SortePickerTest {
    private val names = mapOf(
        "gyokuro" to "Gyokuro",
        "hojicha" to "Hōjicha",
        "hojicha_powder" to "Hōjicha-Pulver",
        "green_generic" to "Grüntee (sonstiger)",
        "herbal" to "Kräutertee",
        "white_generic" to "Weißer Tee",
        "oolong_generic" to "Oolong",
        "black_generic" to "Schwarzer Tee",
        "dark_generic" to "Pu-Erh",
        "other" to "Andere / unbekannt",
    )
    private val categories = mapOf(
        TeaCategory.GREEN to "Grüntee",
        TeaCategory.WHITE to "Weißer Tee",
        TeaCategory.OOLONG to "Oolong",
        TeaCategory.BLACK to "Schwarzer Tee",
        TeaCategory.DARK to "Pu-Erh",
        TeaCategory.HERBAL to "Kräutertee",
    )

    private fun groups(query: String) = SortePicker.groups(query, { names[it] ?: it }, { categories.getValue(it) })

    @Test
    fun `every style shows, grouped by Kategorie in 03's order, with Andere - unbekannt last`() {
        val all = groups("")
        assertThat(all.map { it.category }).containsExactly(
            TeaCategory.GREEN,
            TeaCategory.WHITE,
            TeaCategory.OOLONG,
            TeaCategory.BLACK,
            TeaCategory.DARK,
            TeaCategory.HERBAL,
            null,
        ).inOrder()
        assertThat(all.first().styleKeys).containsAtLeast("gyokuro", "sencha", "matcha", "green_generic").inOrder()
        assertThat(all.first().styleKeys).hasSize(19)
        assertThat(all.last().styleKeys).containsExactly("other")
        assertThat(all.flatMap { it.styleKeys }).hasSize(25)
    }

    @Test
    fun `the search ignores case and accents and matches word starts`() {
        assertThat(groups("hoji").flatMap { it.styleKeys }).containsExactly("hojicha", "hojicha_powder").inOrder()
        assertThat(groups("PULVER").flatMap { it.styleKeys }).containsExactly("hojicha_powder")
    }

    @Test
    fun `a Kategorie's name finds its styles, and groups without a match are left out`() {
        val oolong = groups("oolong")
        assertThat(oolong.map { it.category }).containsExactly(TeaCategory.OOLONG)
        assertThat(groups("unbekannt").single().styleKeys).containsExactly("other")
        assertThat(groups("xyz")).isEmpty()
    }
}
