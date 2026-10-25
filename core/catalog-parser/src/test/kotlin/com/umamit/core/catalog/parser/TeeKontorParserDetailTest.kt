package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The product page parser's detail fields (08 §2.3, §2.8; #66, CAT-03, CAT-11): photo,
 * properties, badges, taste scales, description (sanitised, staff tips stripped), ingredients,
 * the flavoured flag, origin region and the brewing grid and prose, each present and absent.
 * Every page is synthetic (D-016); the cross-selling boxes on every page carry their own photo,
 * badges and description, which must never leak into the product.
 */
class TeeKontorParserDetailTest {
    private fun parse(html: String): ProductPage {
        val result = TeeKontorParser.parseProduct(html.toByteArray(), "$SHOP/morgennebel-sencha/XY0100")
        assertThat(result).isInstanceOf(ProductParse.Parsed::class.java)
        return (result as ProductParse.Parsed).page
    }

    private fun page(
        title: String = "Morgennebel Sencha – 100g",
        description: String? = "<p>Ein erfundener Beschreibungstext für den Test.</p>",
        teamTip: Boolean = false,
        properties: List<Pair<String, List<String>>> = emptyList(),
        badges: List<String> = emptyList(),
        invertedBadges: List<String> = emptyList(),
        scales: List<Pair<String, Int>> = emptyList(),
        brewRows: List<Pair<String, List<String>>> = emptyList(),
        breadcrumb: List<Pair<String, String>> = SyntheticShop.SENCHA_PATH,
        photoPath: String? = SyntheticShop.photoPath(),
    ) = parse(
        SyntheticShop.productPage(
            baseUrl = SHOP,
            title = title,
            description = description,
            teamTip = teamTip,
            properties = properties,
            badges = badges,
            invertedBadges = invertedBadges,
            scales = scales,
            brewRows = brewRows,
            breadcrumb = breadcrumb,
            photoPath = photoPath,
        ),
    )

    @Test
    fun thePhotoIsTheGallerysFullImage() {
        assertThat(page().photoUrl).isEqualTo("$SHOP${SyntheticShop.photoPath()}")
        // Without the product's own gallery image, nothing; never another product's photo.
        assertThat(page(photoPath = null).photoUrl).isNull()
    }

    @Test
    fun everyPropertyOfTheTable() {
        val page = page(
            properties = listOf(
                "Anbaugebiete" to listOf("Japan"),
                "Aroma" to listOf("naturbelassen"),
                "Geschmack" to listOf("grasig", "süßlich"),
                "Hersteller oder Verantwortlicher" to listOf("Erfundene Teefarm GmbH"),
                "Kultivare" to listOf("Yabukita"),
                "Saison" to listOf("Frühling", "Sommer"),
                "Matcha Qualitäten" to listOf("Zeremonie"),
                "Unbekannte Eigenschaft" to listOf("egal"),
            ),
        )

        assertThat(page.originCountry).isEqualTo("Japan")
        assertThat(page.aroma).isEqualTo("naturbelassen")
        // Several values of one property: joined, without the duplicated mobile label.
        assertThat(page.tasteNotes).isEqualTo("grasig, süßlich")
        assertThat(page.manufacturer).isEqualTo("Erfundene Teefarm GmbH")
        assertThat(page.cultivar).isEqualTo("Yabukita")
        assertThat(page.season).isEqualTo("Frühling, Sommer")
        assertThat(page.matchaGrade).isEqualTo("Zeremonie")
    }

    @Test
    fun absentPropertiesStayEmpty() {
        val page = page(description = null)

        assertThat(page.originCountry).isNull()
        assertThat(page.originRegion).isNull()
        assertThat(page.aroma).isNull()
        assertThat(page.tasteNotes).isNull()
        assertThat(page.manufacturer).isNull()
        assertThat(page.cultivar).isNull()
        assertThat(page.season).isNull()
        assertThat(page.matchaGrade).isNull()
        assertThat(page.harvest).isNull()
        assertThat(page.shading).isNull()
        assertThat(page.steaming).isNull()
        assertThat(page.ingredients).isNull()
        assertThat(page.descriptionHtml).isNull()
        assertThat(page.descriptionText).isNull()
        assertThat(page.brewTable).isEmpty()
        assertThat(page.brewText).isNull()
        assertThat(page.tasteScales).isEmpty()
        // The cross-selling boxes show „Bio" and „Ernte: Herbst": not this product's.
        assertThat(page.isOrganic).isFalse()
        assertThat(page.coldBrewSuitable).isFalse()
        assertThat(page.isFlavored).isFalse()
    }

    @Test
    fun badgesAndTheInvertedFactBadges() {
        val page = page(
            badges = listOf("Bio", "Für Einsteiger", "Coldbrew geeignet"),
            invertedBadges = listOf("Dämpfung: Chumushi", "Ernte: Frühjahr 2026", "Beschattung: 20 Tage"),
        )

        assertThat(page.isOrganic).isTrue()
        assertThat(page.coldBrewSuitable).isTrue()
        assertThat(page.steaming).isEqualTo("Chumushi")
        assertThat(page.harvest).isEqualTo("Frühjahr 2026")
        assertThat(page.shading).isEqualTo("20 Tage")
    }

    @Test
    fun tasteScalesCountTheFullLeaves() {
        val page = page(scales = listOf("Umami" to 4, "Bitterstoffe" to 1, "Süße" to 0, "Frische" to 5))

        assertThat(page.tasteScales).containsExactly(
            ShopScale("Umami", 4),
            ShopScale("Bitterstoffe", 1),
            ShopScale("Süße", 0),
            ShopScale("Frische", 5),
        ).inOrder()
    }

    @Test
    fun theDescriptionIsSanitised() {
        val page = page(
            description = """
                <p class="p1">Ein <b>kräftiger</b> Tee <span class="s1">mit</span> <a href="/tee-info/ratgeber" rel="noreferrer noopener" target="_blank">Ratgeber</a>.</p>
                <script>track()</script><style>p{}</style>
                <p>Zeile eins<br>Zeile zwei<img src="https://tracker.example/pixel.gif" width="1" height="1"></p>
                <iframe src="https://www.youtube.com/embed/abc123"></iframe>
                <form action="/x"><input name="q"></form>
                <p></p>
                <p><a href="javascript:alert(1)">böse</a> <a href="https://www.youtube.com/watch?v=abc123">Video</a></p>
            """.trimIndent(),
            teamTip = true,
        )

        val html = requireNotNull(page.descriptionHtml)
        assertThat(html).contains("<b>kräftiger</b>")
        // Relative links become absolute; only href stays.
        assertThat(html).contains("<a href=\"$SHOP/tee-info/ratgeber\">Ratgeber</a>")
        assertThat(html).contains("Zeile eins<br>Zeile zwei")
        // An embedded video becomes a plain link.
        assertThat(html).contains("<a href=\"https://www.youtube.com/embed/abc123\">")
        assertThat(html).contains("<a href=\"https://www.youtube.com/watch?v=abc123\">Video</a>")
        listOf("script", "style", "track()", "<img", "iframe", "<form", "<input", "class=", "rel=", "target=", "<span")
            .forEach { assertThat(html).doesNotContain(it) }
        assertThat(html).doesNotContain("javascript")
        assertThat(html).doesNotContain("<p></p>")
        // The staff tip block is personal data, not tea content (D-076).
        listOf("Unser Tipp", "Erika", "Teeberaterin", "Zitat").forEach { assertThat(html).doesNotContain(it) }

        assertThat(page.descriptionText).isEqualTo(
            "Ein kräftiger Tee mit Ratgeber.\n\nZeile eins\nZeile zwei\n\n" +
                "https://www.youtube.com/embed/abc123\n\nböse Video",
        )
    }

    @Test
    fun ingredientsAndTheFlavouredFlag() {
        val pure = page(
            description = "<p>Ein Tee.</p><p>Zutaten: Grüner Tee* aus Japan</p>" +
                "<p>*aus kontrolliert ökologischem Anbau</p>",
            properties = listOf("Aroma" to listOf("naturbelassen")),
        )
        assertThat(pure.ingredients).isEqualTo("Grüner Tee* aus Japan")
        assertThat(pure.isFlavored).isFalse()

        val genmaicha = page(description = "<p>Zutaten: Grüntee, gerösteter Reis</p>")
        assertThat(genmaicha.isFlavored).isFalse()

        val withPieces = page(description = "<p>Zutaten: Grüner Tee (80 %), Kirschstücke, Aroma</p>")
        assertThat(withPieces.ingredients).isEqualTo("Grüner Tee (80 %), Kirschstücke, Aroma")
        assertThat(withPieces.isFlavored).isTrue()

        val byAroma = page(properties = listOf("Aroma" to listOf("natürlich aromatisiert")))
        assertThat(byAroma.isFlavored).isTrue()

        val bySection = page(
            breadcrumb = listOf(
                "Tee" to "/tee/",
                "Grüner Tee" to "/tee/gruener-tee/",
                "Aromatisierter Grüntee" to "/tee/gruener-tee/aromatisierter-gruentee/",
            ),
        )
        assertThat(bySection.isFlavored).isTrue()
    }

    @Test
    fun theRegionComesFromTheTitleElseTheProse() {
        assertThat(page(title = "Uji Gyokuro Morgentau – 50g").originRegion).isEqualTo("Uji")
        assertThat(
            page(description = "<p>Dieser Tee wächst an den Hängen von Kirishima im Süden.</p>").originRegion,
        ).isEqualTo("Kirishima")
        assertThat(page(description = "<p>Ein Tee ohne Herkunftsangabe im Text.</p>").originRegion).isNull()
        // A region name inside another word is none.
        assertThat(page(description = "<p>Ein Tee mit Fujianerei-Muster.</p>").originRegion).isNull()
    }

    @Test
    fun theBrewingGridIsReadOnceAndVerbatim() {
        val page = page(
            brewRows = listOf(
                "westlich" to listOf("~10g/L", "~65°C", "~1 Min", "-", "-"),
                "japan" to listOf("~4g/100ml", "~60°C", "~90 Sek", "~20 Sek", "~60 Sek"),
            ),
        )

        assertThat(page.brewTable).containsExactly(
            BrewGridRow(
                kind = "westlich",
                cells = listOf(
                    BrewGridCell("Menge", "~10g/L"),
                    BrewGridCell("Temperatur", "~65°C"),
                    BrewGridCell("1. Aufguss", "~1 Min"),
                    BrewGridCell("2. Aufguss", "-"),
                    BrewGridCell("3. Aufguss", "-"),
                ),
            ),
            BrewGridRow(
                kind = "japan",
                cells = listOf(
                    BrewGridCell("Menge", "~4g/100ml"),
                    BrewGridCell("Temperatur", "~60°C"),
                    BrewGridCell("1. Aufguss", "~90 Sek"),
                    BrewGridCell("2. Aufguss", "~20 Sek"),
                    BrewGridCell("3. Aufguss", "~60 Sek"),
                ),
            ),
        ).inOrder()
    }

    @Test
    fun theBrewingProseIsTheDescriptionsBrewingInstructionsOnly() {
        val page = page(
            description = "<p>Ein Tee aus den Bergen.</p>" +
                "<p>Zubereitung: 4 g auf 100 ml bei 60 °C für 90 Sekunden.</p>" +
                "<p>Für einen Kaltaufguss beträgt die Ziehzeit 20 - 30 Minuten im Kühlschrank.</p>" +
                // Live sample 1 (structure only): words and amounts that are no brewing instruction.
                "<p>Auch als Kaltaufguss ein Genuss.</p>" +
                "<p>Der Aufguss hat eine leuchtend grüne Farbe und duftet nach Gras.</p>" +
                "<p>In diesem Video zeigen wir die Zubereitung: <a href=\"https://video.example/v\">Video</a></p>" +
                "<p>Auch in der 50g-Vorteilspackung erhältlich.</p>" +
                "<p>30g in einer Dose, Höhe ca. 7 cm, Durchmesser ca. 5 cm.</p>" +
                "<p>Kühl und trocken lagern, nach dem Öffnen in 4 Wochen verbrauchen.</p>" +
                "<p>Zutaten: Grüner Tee</p>",
        )

        assertThat(page.brewText).isEqualTo(
            "Zubereitung: 4 g auf 100 ml bei 60 °C für 90 Sekunden.\n\n" +
                "Für einen Kaltaufguss beträgt die Ziehzeit 20 - 30 Minuten im Kühlschrank.",
        )
    }

    /**
     * Live sample 2 (structure only, D-089): a video pointer with a time and the cold brew is no
     * instruction; bags per volume without temperature, time or grams are one.
     */
    @Test
    fun aVideoPointerIsNoBrewingProseButBagsPerVolumeAre() {
        val page = page(
            description = "<p>Ein erfundener Tee für kalte Tage.</p>" +
                "<p>In diesem Video zeigen wir, wie der Kaltaufguss in nur 1 Minute gelingt.</p>" +
                "<p>Einen Beutel auf 0,3–0,5 Liter kaltes Wasser geben, kurz schütteln und zweimal aufgießen.</p>",
        )

        assertThat(page.brewText)
            .isEqualTo("Einen Beutel auf 0,3–0,5 Liter kaltes Wasser geben, kurz schütteln und zweimal aufgießen.")
    }

    @Test
    fun withoutABrewingInstructionThereIsNoProse() {
        val page = page(
            description = "<p>Der zweite Aufguss darf kürzer ziehen.</p>" +
                "<p>Eine Dose mit 30 g, dazu 2 Löffel.</p>",
        )

        assertThat(page.brewText).isNull()
    }

    @Test
    fun controlCharactersAreRemovedFromTexts() {
        val page = page(
            properties = listOf("Hersteller oder Verantwortlicher" to listOf("Erfundene Teefarm GmbH\u0007Teeweg 1")),
            description = "<p>Ein\u0007 Tee\u0085 aus dem Tal.</p>",
        )

        assertThat(page.manufacturer).isEqualTo("Erfundene Teefarm GmbH Teeweg 1")
        assertThat(page.descriptionText).isEqualTo("Ein Tee aus dem Tal.")
        assertThat(
            requireNotNull(page.descriptionHtml),
        ).doesNotContainMatch("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F]")
    }

    private companion object {
        const val SHOP = "https://shop.example"
    }
}
