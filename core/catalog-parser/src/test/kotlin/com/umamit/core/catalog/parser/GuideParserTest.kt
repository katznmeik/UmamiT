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
            "$SHOP/tee-info/matcha-latte-rezept",
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
            "matcha-latte-rezept",
        ).inOrder()
        assertThat(GuideUrls.select(urls).first().url).isEqualTo("$SHOP/tee-info/UMAMI-erklaert")
    }

    @Test
    fun atMostFortyGuidesAreSelectedTheMostRelevantFirst() {
        val recipes = (10..53).map { "$SHOP/tee-info/ein-langes-matcha-rezept-nr-$it" }
        val urls = recipes + "$SHOP/tee-info/sencha-und-gyokuro-im-vergleich" + "$SHOP/tee-info/umami"

        val selected = GuideUrls.select(urls).map { it.slug }

        assertThat(GuideUrls.MAX_GUIDES).isEqualTo(40)
        assertThat(selected).hasSize(40)
        // Two keywords first, then the shorter slug; the longest recipe numbers are left out.
        assertThat(selected).containsAtLeast("sencha-und-gyokuro-im-vergleich", "umami")
        assertThat(selected).doesNotContain("ein-langes-matcha-rezept-nr-53")
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
