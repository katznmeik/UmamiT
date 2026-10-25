package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** The style mapping of 08 §2.6 (CAT-06) on invented names (D-016). */
class StyleMapperTest {
    private data class Case(
        val path: String?,
        val name: String,
        val style: String,
        val flavored: Boolean = false,
        val unmapped: Boolean = false,
        val form: TeaForm = TeaForm.LOOSE_LEAF,
        val japanese: Boolean = true,
    )

    @Test
    fun theBreadcrumbsLastSegmentElseTheNameGivesTheStyle() {
        val japan = "/tee/gruener-tee/japan"
        listOf(
            Case("$japan/gyokuro/", "Tautropfen", "gyokuro"),
            Case("$japan/kabusecha/", "Schattenblatt", "kabusecha"),
            Case("$japan/sencha/", "Morgennebel Sencha", "sencha"),
            Case("$japan/sencha/", "Morgennebel Fukamushi Sencha", "fukamushi_sencha"),
            Case("$japan/sencha/", "Yuzu Sencha", "flavored_green", flavored = true),
            Case("/tee/gruener-tee/shincha-2026/", "Erster Frühling", "shincha"),
            Case("$japan/shincha/", "Erster Frühling", "shincha"),
            Case("$japan/matcha/", "Grüne Wolke", "matcha", form = TeaForm.POWDER),
            Case("/matcha/matcha-teepulver/", "Grüne Wolke Latte", "matcha", flavored = true, form = TeaForm.POWDER),
            Case("$japan/kukicha-karigane-shiraore/", "Stängelchen", "karigane"),
            Case("$japan/kukicha/", "Stängelchen", "karigane"),
            Case("$japan/bancha/", "Großblatt", "bancha"),
            Case("$japan/houjicha/", "Röstblatt", "hojicha"),
            Case("$japan/houjicha/", "Röstblatt Pulver", "hojicha", form = TeaForm.POWDER),
            Case("$japan/genmaicha/", "Reisfeld", "genmaicha"),
            Case("$japan/kamairicha/", "Pfannenblatt", "kamairicha"),
            Case("$japan/tamaryokucha/", "Lockenblatt", "tamaryokucha"),
            Case(
                "/tee/gruener-tee/aromatisierter-gruentee/",
                "Kirschblüte",
                "flavored_green",
                flavored = true,
                japanese = false,
            ),
            // The section is generic: the name decides.
            Case("/tee/gruener-tee/bio-gruentee/", "Bio Gyokuro Morgentau", "gyokuro"),
            Case("/tee/gruener-tee/bio-gruentee/", "Bio Genmaicha mit Matcha", "genmaicha"),
            Case("/tee/gruener-tee/bio-gruentee/", "Gyokuro Karigane", "karigane"),
            Case("/tee/gruener-tee/aromatisierter-gruentee/", "Bio Yuzu Sencha", "flavored_green", flavored = true),
            Case("/tee/gruener-tee/china/", "Long Jing Drachenbrunnen", "longjing", japanese = false),
            Case("/tee/gruener-tee/china/", "Lung Ching Spezial", "longjing", japanese = false),
            Case("/tee/gruener-tee/china/", "Bi Luo Chun Frühling", "bi_luo_chun", japanese = false),
            Case("/tee/gruener-tee/china/", "Anji Weißblatt", "anji_bai_cha", japanese = false),
            Case("/tee/gruener-tee/andere-regionen/", "Sejak aus Hadong", "korean_green", japanese = false),
            // Unknown: the generic style, flagged; a flavoured one is the flavoured style.
            Case("/tee/gruener-tee/china/", "Wolkenspitze", "green_generic", unmapped = true, japanese = false),
            Case(null, "Wolkenspitze", "green_generic", unmapped = true, japanese = false),
            Case(
                "/tee/gruener-tee/andere-regionen/",
                "Pfirsichwolke",
                "flavored_green",
                flavored = true,
                japanese = false,
            ),
        ).forEach(::check)
    }

    @Test
    fun theShopsSpellingsAndATeaFamilysPageMapToo() {
        listOf(
            // The shop's spellings (live sample 2, D-089): „Bi Lo Chun", „Pi Lo Chun" and every Korean grade.
            Case("/tee/gruener-tee/china/", "Bi Lo Chun Frühling", "bi_luo_chun", japanese = false),
            Case("/tee/gruener-tee/china/", "Pi Lo Chun Spezial", "bi_luo_chun", japanese = false),
            Case("/tee/gruener-tee/china/", "Biluochun Bergnebel", "bi_luo_chun", japanese = false),
            Case("/tee/gruener-tee/andere-regionen/", "Hügelgarten Joongjak", "korean_green", japanese = false),
            Case("/tee/gruener-tee/andere-regionen/", "Hügelgarten Jungjak", "korean_green", japanese = false),
            Case("/tee/gruener-tee/andere-regionen/", "Hügelgarten Daejak", "korean_green", japanese = false),
            Case("/tee/gruener-tee/andere-regionen/", "Hügelgarten Woojeon", "korean_green", japanese = false),
            // A tea family's page (D-089): the name decides.
            Case("/tee/japantee/teefamilien/familie-beispiel/", "Bergdorf Fukamushi", "fukamushi_sencha"),
        ).forEach(::check)
    }

    private fun check(case: Case) {
        val match = StyleMapper.map(categoryPath = case.path, name = case.name, isFlavored = case.flavored)
        val check = assertWithMessage("${case.path} ${case.name}")
        check.that(match.style.key).isEqualTo(case.style)
        check.that(match.unmapped).isEqualTo(case.unmapped)
        check.that(match.form).isEqualTo(case.form)
        check.that(match.japanese).isEqualTo(case.japanese)
        check.that(match.coldBrewDefault).isFalse()
    }

    @Test
    fun mizudashiBagsTakeTheStyleOfTheirNameAndBrewColdByDefault() {
        val path = "/tee/gruener-tee/japan/mizudashi-kaltaufgussbeutel/"

        val sencha = StyleMapper.map(categoryPath = path, name = "Kühle Brise Sencha Beutel", isFlavored = false)
        assertThat(sencha.style.key).isEqualTo("sencha")
        assertThat(sencha.form).isEqualTo(TeaForm.TEA_BAG)
        assertThat(sencha.coldBrewDefault).isTrue()
        assertThat(sencha.unmapped).isFalse()

        val plain = StyleMapper.map(categoryPath = path, name = "Kühle Brise", isFlavored = false)
        assertThat(plain.style.key).isEqualTo("green_generic")
        assertThat(plain.unmapped).isFalse()
        assertThat(plain.coldBrewDefault).isTrue()
    }

    /** Live sample 2 (D-089): cold-brew bags filed under Sencha are Mizudashi by their name. */
    @Test
    fun aNameSayingColdBrewBagsMakesAMizudashiWhateverTheBreadcrumb() {
        val sencha = "/tee/gruener-tee/japan/sencha/"
        listOf("Kühle Brise Mizudashi – 20 Aufgussbeutel", "Kühle Brise Kaltaufgussbeutel").forEach { name ->
            val match = StyleMapper.map(categoryPath = sencha, name = name)
            val check = assertWithMessage(name)
            check.that(match.style.key).isEqualTo("sencha")
            check.that(match.form).isEqualTo(TeaForm.TEA_BAG)
            check.that(match.coldBrewDefault).isTrue()
            check.that(match.unmapped).isFalse()
        }

        // Hot tea bags are bags, but brew hot; a refill bag is loose leaf.
        val hotBags = StyleMapper.map(categoryPath = sencha, name = "Morgennebel Sencha – 20 Teebeutel")
        assertThat(hotBags.form).isEqualTo(TeaForm.TEA_BAG)
        assertThat(hotBags.coldBrewDefault).isFalse()
        assertThat(StyleMapper.map(categoryPath = sencha, name = "Morgennebel Sencha Nachfüllbeutel").form)
            .isEqualTo(TeaForm.LOOSE_LEAF)
    }

    @Test
    fun aKoreanTeaNoKeywordNamesIsAKoreanGreenByItsOrigin() {
        val match = StyleMapper.map(
            categoryPath = "/tee/gruener-tee/andere-regionen/",
            name = "Hügelgarten Frühling",
            originCountry = "Südkorea",
        )

        assertThat(match.style.key).isEqualTo("korean_green")
        assertThat(match.unmapped).isFalse()
    }

    @Test
    fun theShopsTypeCountsLikeTheName() {
        val match = StyleMapper.map(categoryPath = "/tee/gruener-tee/", name = "Morgentau", teaType = "Gyokuro")

        assertThat(match.style.key).isEqualTo("gyokuro")
    }

    @Test
    fun theUmamiScaleGivesTheLevelElseTheStyle() {
        val sencha = BuiltInStyles.byKey("sencha")!!
        listOf(
            0 to UmamiLevel.NONE,
            1 to UmamiLevel.LOW,
            2 to UmamiLevel.MEDIUM,
            3 to UmamiLevel.HIGH,
            4 to UmamiLevel.VERY_HIGH,
            5 to UmamiLevel.VERY_HIGH,
        ).forEach { (leaves, level) ->
            assertThat(StyleMapper.umamiLevel(listOf(ShopScale("Bitterstoffe", 2), ShopScale("Umami", leaves)), sencha))
                .isEqualTo(level)
        }
        assertThat(StyleMapper.umamiLevel(listOf(ShopScale("Bitterstoffe", 2)), sencha)).isEqualTo(UmamiLevel.MEDIUM)
        assertThat(
            StyleMapper.umamiLevel(emptyList(), BuiltInStyles.byKey("gyokuro")!!),
        ).isEqualTo(UmamiLevel.VERY_HIGH)
    }
}
