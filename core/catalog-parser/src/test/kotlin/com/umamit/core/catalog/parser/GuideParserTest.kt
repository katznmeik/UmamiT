package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline.LineBreak
import com.umamit.core.model.GuideInline.Span
import org.junit.Test
import java.time.OffsetDateTime

/**
 * *Tee-Info* guides (CAT-09, 08 §2.7, §2.8, D-087): which articles the refresh selects, how an
 * article is converted (sanitised text, images, links) and how its links and images are resolved
 * once the refresh knows the catalog. Every page is synthetic (D-016).
 */
class GuideParserTest {
    private fun parse(html: String, slug: String = SLUG): GuideArticle {
        val result = GuideParser.parse(html.toByteArray(), "$SHOP/tee-info/$slug")
        assertThat(result).isInstanceOf(GuideParse.Parsed::class.java)
        return (result as GuideParse.Parsed).article
    }

    private fun body(html: String): List<GuideBlock> =
        parse(SyntheticGuide.page(baseUrl = SHOP, body = html)).body.blocks

    // --- selection ---

    @Test
    fun guidesAreSelectedFromTheSitemapBySlugKeywords() {
        val urls = listOf(
            "$SHOP/tee-info/ein-guide-fuer-japantee",
            "$SHOP/tee-info/matcha-fuer-einsteiger",
            "$SHOP/tee-info/kaltaufguss-im-sommer",
            "$SHOP/tee-info/UMAMI-erklaert",
            "$SHOP/tee-info/schwarztee-aus-assam",
            "$SHOP/tee-info/",
            "$SHOP/tee-info/sencha/kommentare",
            "$SHOP/sencha-tasse/XY0100",
            "$SHOP/tee/gruener-tee/japan/sencha/",
            "$SHOP/tee-info/ein-guide-fuer-japantee",
        )

        assertThat(GuideUrls.select(urls).map { it.slug }).containsExactly(
            "UMAMI-erklaert",
            "ein-guide-fuer-japantee",
            "kaltaufguss-im-sommer",
            "matcha-fuer-einsteiger",
        ).inOrder()
        assertThat(GuideUrls.select(urls).first().url).isEqualTo("$SHOP/tee-info/UMAMI-erklaert")
    }

    @Test
    fun atMostFortyGuidesAreSelectedTheMostRelevantFirst() {
        val recipes = (10..53).map { "$SHOP/tee-info/ein-langer-matcha-text-nr-$it" }
        val urls = recipes + "$SHOP/tee-info/sencha-und-gyokuro-im-vergleich" + "$SHOP/tee-info/umami"

        val selected = GuideUrls.select(urls).map { it.slug }

        assertThat(GuideUrls.MAX_GUIDES).isEqualTo(40)
        assertThat(selected).hasSize(40)
        // Two keywords first, then the shorter slug; the longest recipe numbers are left out.
        assertThat(selected).containsAtLeast("sencha-und-gyokuro-im-vergleich", "umami")
        assertThat(selected).doesNotContain("ein-langer-matcha-text-nr-53")
        assertThat(selected).isInOrder()
    }

    @Test
    fun everyKeywordOf08Section27SelectsAGuide() {
        val keywords = listOf(
            "japantee", "gruentee", "matcha", "sencha", "gyokuro", "kabuse", "shincha", "houjicha", "genmaicha",
            "kaltaufguss", "kyusu", "umami",
        )

        assertThat(GuideUrls.select(keywords.map { "$SHOP/tee-info/alles-ueber-$it" })).hasSize(keywords.size)
    }

    /**
     * Live sample 2 (structure only, D-089): promotions, events and recipes are no guides, also
     * when they name a keyword; green-tea guides on types the keywords missed are.
     */
    @Test
    fun promotionsEventsAndRecipesAreNoGuidesAndEveryGreenTypeSelectsOne() {
        val noise = listOf(
            "sonstiges-matcha-aktion-erfunden",
            "sonstiges-shincha-1999",
            "teeverkostungen-shincha-abend",
            "aktionstees-sencha-woche",
            "blog-sencha-event-im-laden",
            "teerezepte-matcha-limonade",
            "matcha-plaetzchen-erfunden",
            "houjicha-kekse-vegan",
            "matcha-beeren-traum-dessert",
            "sencha-spritz-erfunden",
            "gyokuro-tasting-abend",
        )
        val guides = listOf(
            "aracha-kurz-erklaert",
            "mizudashi-im-sommer",
            "mizucha-im-kuehlschrank",
            "fukamushi-und-asamushi",
            "kultivare-im-ueberblick",
            "bancha-fuer-den-alltag",
            "kukicha-erklaert",
            "tamaryokucha-kennenlernen",
            "kamairicha-aus-der-pfanne",
            "tencha-erklaert",
        )

        val selected = GuideUrls.select((noise + guides).map { "$SHOP/tee-info/$it" }).map { it.slug }

        assertThat(selected).containsExactlyElementsIn(guides)
    }

    // --- article ---

    @Test
    fun titleDateAndHeroComeFromTheBlogPosting() {
        val article = parse(SyntheticGuide.page(baseUrl = SHOP, slug = SLUG, title = "Erfundener Guide"))

        assertThat(article.slug).isEqualTo(SLUG)
        assertThat(article.url).isEqualTo("$SHOP/tee-info/$SLUG")
        assertThat(article.title).isEqualTo("Erfundener Guide")
        assertThat(article.publishedAtMillis)
            .isEqualTo(OffsetDateTime.parse("2024-03-12T09:30:00+01:00").toInstant().toEpochMilli())
        assertThat(article.heroUrl).isEqualTo("$SHOP${SyntheticGuide.imagePath("hero")}")
        // The title is the page's, never a paragraph of the body; the teaser next to it is no part of it.
        assertThat(article.body.blocks).containsExactly(GuideBlock.Paragraph(listOf(Span("Ein erfundener Absatz."))))
    }

    @Test
    fun withoutABlogPostingTheTitleIsTheHeadingAndThereIsNoHeroOrDate() {
        val article = parse(SyntheticGuide.page(baseUrl = SHOP, title = "Überschrift im Artikel", jsonLd = null))

        assertThat(article.title).isEqualTo("Überschrift im Artikel")
        assertThat(article.publishedAtMillis).isNull()
        assertThat(article.heroUrl).isNull()
    }

    @Test
    fun aPageWithoutTheArticleBlockIsNoGuide() {
        val result = GuideParser.parse(
            SyntheticGuide.page(baseUrl = SHOP, bodyBlock = false).toByteArray(),
            "$SHOP/tee-info/x",
        )

        assertThat(result).isEqualTo(GuideParse.Failed)
    }

    @Test
    fun formattingIsKeptAndHeadingsBecomeHeadings() {
        val blocks = body(
            "<h2>Erste   Überschrift</h2>" +
                "<p>Ein <strong>kräftiger</strong> und <em>milder</em>\n Tee<br>in zwei Zeilen.</p>" +
                "<p><b>Fette Zeile als Überschrift</b><br>und ihr Text.</p>" +
                "<p><b>Ganz fetter Absatz</b></p>" +
                "<ul><li>eins</li><li><b>zwei</b></li></ul><ol><li>erstens</li></ol>" +
                "<table><tr><th>Menge</th><th>Zeit</th></tr><tr><td>4 g</td><td>60 Sek</td></tr></table>",
        )

        assertThat(blocks).containsExactly(
            GuideBlock.Heading("Erste Überschrift"),
            GuideBlock.Paragraph(
                listOf(
                    Span("Ein "),
                    Span("kräftiger", bold = true),
                    Span(" und "),
                    Span("milder", italic = true),
                    Span(" Tee"),
                    LineBreak,
                    Span("in zwei Zeilen."),
                ),
            ),
            GuideBlock.Heading("Fette Zeile als Überschrift"),
            GuideBlock.Paragraph(listOf(Span("und ihr Text."))),
            GuideBlock.Heading("Ganz fetter Absatz"),
            GuideBlock.ListBlock(
                ordered = false,
                items = listOf(listOf(Span("eins")), listOf(Span("zwei", bold = true))),
            ),
            GuideBlock.ListBlock(ordered = true, items = listOf(listOf(Span("erstens")))),
            GuideBlock.ListBlock(
                ordered = false,
                items = listOf(listOf(Span("Menge · Zeit")), listOf(Span("4 g · 60 Sek"))),
            ),
        ).inOrder()
    }

    @Test
    fun scriptsFormsStaffTipsAndEmbedsAreStrippedAVideoBecomesAPlainLink() {
        val blocks = body(
            "<p>Vorher.<script>alert(1)</script><style>p{}</style></p>" +
                "<form><input value=\"x\"><p>Formular</p></form>" +
                "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>" +
                "<div class=\"st-teamtipp\"><h4>Erika Beispiel</h4><p>Ein Zitat.</p></div>" +
                "<p>Nach\u0007her.</p>",
        )

        assertThat(blocks).containsExactly(
            GuideBlock.Paragraph(listOf(Span("Vorher."))),
            GuideBlock.Paragraph(
                listOf(Span("https://www.youtube.com/embed/abc123", link = "https://www.youtube.com/embed/abc123")),
            ),
            GuideBlock.Paragraph(listOf(Span("Nach her."))),
        ).inOrder()
    }

    @Test
    fun linksAreAbsoluteHttpLinksOnly() {
        val blocks = body(
            "<p><a href=\"/tee-info/ein-anderer-guide\">Guide</a>, <a href=\"javascript:alert(1)\">Skript</a>, " +
                "<a href=\"mailto:x@example.com\">Mail</a> und <a href=\"https://example.org/x\"><b>fremd</b></a>.</p>",
        )

        assertThat(blocks).containsExactly(
            GuideBlock.Paragraph(
                listOf(
                    Span("Guide", link = "$SHOP/tee-info/ein-anderer-guide"),
                    Span(", Skript, Mail und "),
                    Span("fremd", bold = true, link = "https://example.org/x"),
                    Span("."),
                ),
            ),
        )
    }

    @Test
    fun theGallerysImagesAreTakenOnceEachWithoutTheHeroOrLazyPlaceholders() {
        val article = parse(
            SyntheticGuide.page(
                baseUrl = SHOP,
                gallery = listOf(
                    SyntheticGuide.imagePath("hero"),
                    SyntheticGuide.imagePath("a"),
                    SyntheticGuide.imagePath("b"),
                ),
                body = "<p>Text <img src=\"$SHOP${SyntheticGuide.imagePath("c")}\" alt=\"Kanne\"> weiter.</p>" +
                    "<p><img src=\"$SHOP/media/pixel.gif\" width=\"1\" height=\"1\"></p>" +
                    "<p><img src=\"data:image/gif;base64,R0lGOD\"></p>",
            ),
        )

        assertThat(article.body.blocks).containsExactly(
            GuideBlock.Image("$SHOP${SyntheticGuide.imagePath("a")}"),
            GuideBlock.Image("$SHOP${SyntheticGuide.imagePath("b")}"),
            GuideBlock.Paragraph(listOf(Span("Text"))),
            GuideBlock.Image("$SHOP${SyntheticGuide.imagePath("c")}", alt = "Kanne"),
            GuideBlock.Paragraph(listOf(Span("weiter."))),
        ).inOrder()
        assertThat(article.imageUrls).containsExactly(
            "$SHOP${SyntheticGuide.imagePath("hero")}",
            "$SHOP${SyntheticGuide.imagePath("a")}",
            "$SHOP${SyntheticGuide.imagePath("b")}",
            "$SHOP${SyntheticGuide.imagePath("c")}",
        ).inOrder()
    }

    /**
     * Live sample 2 (structure only, D-089): the gallery writes an image's URL with raw spaces,
     * the body and the JSON-LD hero with `%20`; each image is one, stored encoded.
     */
    @Test
    fun anImageIsOneWhetherItsUrlHasSpacesOrPercent20() {
        val raw = "/media/gg/hh/ii/1700000000/zwei bilder im glas.jpg?ts=1700000000"
        val heroRaw = "/media/gg/hh/ii/1700000000/erstes bild.jpg?ts=1700000000"
        val encoded = raw.replace(" ", "%20")
        val heroEncoded = heroRaw.replace(" ", "%20")
        val article = parse(
            SyntheticGuide.page(
                baseUrl = SHOP,
                jsonLd = SyntheticGuide.blogPosting(SHOP, SLUG, "Ein Guide", image = "$SHOP$heroEncoded"),
                gallery = listOf(heroRaw, raw),
                body = "<p>Text.</p><img class=\"lazyload\" data-src=\"$SHOP$encoded\" alt=\"\">",
            ),
        )

        assertThat(article.body.blocks).containsExactly(
            GuideBlock.Image("$SHOP$encoded"),
            GuideBlock.Paragraph(listOf(Span("Text."))),
        ).inOrder()
        assertThat(article.imageUrls).containsExactly("$SHOP$heroEncoded", "$SHOP$encoded").inOrder()
    }

    /**
     * Live sample 2 (structure only, D-089): the article block ends with the comment section, starts
     * with the blog's date list, introduces a player that isn't there and closes with a tag line.
     */
    @Test
    fun theCommentSectionTheDateListAVideoIntroWithoutVideoAndATagLineAreLeftOut() {
        val blocks = body(
            "<ul class=\"list-unstyled\"><li><span class=\"icon icon-calendar\"></span> 12/03/2024</li></ul>" +
                "<p>Ein erfundener Absatz über Tee.</p>" +
                "<p>Wie das geht, zeigen wir in diesem Video Schritt für Schritt:</p>" +
                "<p>Noch ein Absatz.</p>" +
                "<p>Sommer Eistee kaltgebrüht</p>" +
                "<div class=\"pixel-blog-questions pixel-blog-questions__standard\">" +
                "<h3>Kommentare und Fragen (0)</h3><div class=\"alert alert-info\">Noch keine Kommentare.</div>" +
                "<h4>Kommentar schreiben</h4><p>Ein Hinweis.</p>" +
                "<p>Anmeldung erforderlich <a href=\"$SHOP/account/login\">Anmelden</a></p></div>",
        )

        assertThat(blocks).containsExactly(
            GuideBlock.Paragraph(listOf(Span("Ein erfundener Absatz über Tee."))),
            GuideBlock.Paragraph(listOf(Span("Noch ein Absatz."))),
        ).inOrder()
    }

    @Test
    fun aVideoIntroFollowedByItsPlayerStays() {
        val blocks = body(
            "<p>So geht es in diesem Video:</p><iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>" +
                "<p>Ein letzter Satz mit Punkt.</p>",
        )

        assertThat(blocks).hasSize(3)
    }

    // --- resolution ---

    @Test
    fun teaAndGuideLinksResolveInsideTheAppEverythingElseStaysForTheBrowser() {
        val article = parse(
            SyntheticGuide.page(
                baseUrl = SHOP,
                body = "<p><a href=\"/morgennebel-sencha/XY0100-50\">Tee</a> " +
                    "<a href=\"/merged-sencha-100g/XY0201\">Zweite Nummer</a> " +
                    "<a href=\"/teekanne/ZZ0900\">Kanne</a> " +
                    "<a href=\"/tee-info/matcha-guide\">Guide</a> " +
                    "<a href=\"/tee-info/schwarztee-guide\">Anderer Guide</a> " +
                    "<a href=\"/tee/gruener-tee/\">Kategorie</a> " +
                    "<a href=\"https://example.org/morgennebel-sencha/XY0100\">Fremd</a> " +
                    "<a href=\"https://www.shop.example/morgennebel-sencha/XY0100\">Mit www</a></p>",
            ),
        )
        val links = GuideLinkResolver(
            shopHost = "shop.example",
            catalogKeys = mapOf("XY0100" to "XY0100", "XY0200" to "XY0200", "XY0201" to "XY0200"),
            guideSlugs = setOf("matcha-guide", SLUG),
        )

        val resolved = article.resolve(imageFiles = emptyMap(), links = links)

        val targets = (resolved.body.blocks.single() as GuideBlock.Paragraph).inlines
            .filterIsInstance<Span>().mapNotNull { it.link }
        assertThat(targets).containsExactly(
            "umamit://catalog/XY0100",
            "umamit://catalog/XY0200",
            "$SHOP/teekanne/ZZ0900",
            "umamit://guide/matcha-guide",
            "$SHOP/tee-info/schwarztee-guide",
            "$SHOP/tee/gruener-tee/",
            "https://example.org/morgennebel-sencha/XY0100",
            "umamit://catalog/XY0100",
        ).inOrder()
        assertThat(resolved.linkedCatalogKeys).containsExactly("XY0100", "XY0200").inOrder()
    }

    @Test
    fun imagesBecomeTheirLocalFilesAndThoseWithoutOneAreLeftOut() {
        val hero = "$SHOP${SyntheticGuide.imagePath("hero")}"
        val a = "$SHOP${SyntheticGuide.imagePath("a")}"
        val article = parse(
            SyntheticGuide.page(
                baseUrl = SHOP,
                gallery = listOf(SyntheticGuide.imagePath("a"), SyntheticGuide.imagePath("b")),
            ),
        )

        val resolved = article.resolve(
            imageFiles = mapOf(hero to "g_hero.webp", a to "g_a.webp"),
            links = GuideLinkResolver("shop.example", emptyMap(), emptySet()),
        )

        assertThat(resolved.heroFile).isEqualTo("g_hero.webp")
        assertThat(resolved.body).isEqualTo(
            GuideDocument(
                listOf(GuideBlock.Image("g_a.webp"), GuideBlock.Paragraph(listOf(Span("Ein erfundener Absatz.")))),
            ),
        )
    }

    private companion object {
        const val SHOP = "https://shop.example"
        const val SLUG = "erfundener-gruentee-guide"
    }
}
