package com.umamit.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The vocabulary of 08 §4: every key is pinned, so rewording a label never changes a key (D-112). */
class TasteDescriptorTest {

    @Test
    @Suppress("LongMethod")
    fun `every key is pinned in the order of the table`() {
        assertThat(TasteDescriptor.entries.map { it.key }).containsExactly(
            "marine.algen",
            "marine.nori",
            "marine.kombu",
            "marine.meeresbrise",
            "marine.dashi",
            "vegetal.spinat",
            "vegetal.zuckerschote",
            "vegetal.edamame",
            "vegetal.spargel",
            "vegetal.gedaempftes_gemuese",
            "vegetal.gemaehtes_gras",
            "vegetal.heu",
            "vegetal.artischocke",
            "sweet.zuckermais",
            "sweet.esskastanie",
            "sweet.honig",
            "sweet.karamell",
            "sweet.rohrzucker",
            "sweet.suesskartoffel",
            "sweet.vanille",
            "roasted_nutty.geroesteter_reis",
            "roasted_nutty.geroestete_nuesse",
            "roasted_nutty.toast",
            "roasted_nutty.kakao",
            "roasted_nutty.kaffee",
            "roasted_nutty.rauch",
            "floral.orchidee",
            "floral.jasmin",
            "floral.osmanthus",
            "floral.rose",
            "floral.geissblatt",
            "floral.kirschbluete",
            "fruity.zitrus",
            "fruity.yuzu",
            "fruity.steinobst",
            "fruity.muskateller",
            "fruity.melone",
            "fruity.trockenfruechte",
            "fruity.beeren",
            "earthy_woody.waldboden",
            "earthy_woody.pilze",
            "earthy_woody.holz",
            "earthy_woody.leder",
            "earthy_woody.kampfer",
            "earthy_woody.mineralisch",
            "spice_herb.minze",
            "spice_herb.kiefer",
            "spice_herb.zimt",
            "spice_herb.kraeuter",
            "mouthfeel.cremig",
            "mouthfeel.seidig",
            "mouthfeel.vollmundig",
            "mouthfeel.frisch_herb",
            "mouthfeel.trocknend",
            "mouthfeel.belegend",
            "mouthfeel.klarer_abgang",
            "mouthfeel.anhaltende_suesse",
            "off_note.abgestanden",
            "off_note.flach",
            "off_note.zu_bitter",
            "off_note.metallisch",
            "off_note.muffig",
            "off_note.sauer",
        ).inOrder()
    }

    @Test
    fun `the ten groups are in the order of the table`() {
        assertThat(DescriptorGroup.entries.map { it.key }).containsExactly(
            "marine",
            "vegetal",
            "sweet",
            "roasted_nutty",
            "floral",
            "fruity",
            "earthy_woody",
            "spice_herb",
            "mouthfeel",
            "off_note",
        ).inOrder()
    }

    @Test
    fun `keys are unique and prefixed by their group`() {
        assertThat(TasteDescriptor.entries.map { it.key }.toSet()).hasSize(TasteDescriptor.entries.size)
        TasteDescriptor.entries.forEach { assertThat(it.key).startsWith(it.group.key + ".") }
    }

    @Test
    fun `lookup finds a stored key and ignores an unknown one`() {
        assertThat(TasteDescriptor.fromKey("marine.nori")).isEqualTo(TasteDescriptor.MARINE_NORI)
        assertThat(TasteDescriptor.fromKey("marine.unknown")).isNull()
        assertThat(TasteDescriptor.fromKey("")).isNull()
    }

    @Test
    fun `descriptors of a group keep the table order`() {
        assertThat(TasteDescriptor.inGroup(DescriptorGroup.SPICE_HERB).map { it.key })
            .containsExactly(
                "spice_herb.minze",
                "spice_herb.kiefer",
                "spice_herb.zimt",
                "spice_herb.kraeuter",
            ).inOrder()
    }
}
