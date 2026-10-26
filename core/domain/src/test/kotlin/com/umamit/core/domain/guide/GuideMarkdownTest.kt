package com.umamit.core.domain.guide

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline.LineBreak
import com.umamit.core.model.GuideInline.Span
import org.junit.Test

/** A guide's body as `catalog_guide.body_markdown` stores it (05 §4.3, CAT-09, D-087). */
class GuideMarkdownTest {
    private val document = GuideDocument(
        listOf(
            GuideBlock.Heading("Was ist ein erfundener Tee?"),
            GuideBlock.Paragraph(
                listOf(
                    Span("Ein "),
                    Span("kräftiger", bold = true),
                    Span(" und "),
                    Span("milder", italic = true),
                    Span(" Tee"),
                    LineBreak,
                    Span("mit "),
                    Span("Link", link = "https://shop.example/tee-info/erfundener-guide"),
                    Span(" und "),
                    Span("fettem Link", bold = true, link = "umamit://catalog/XY0100"),
                    Span("."),
                ),
            ),
            GuideBlock.Image(source = "g_0123abcd.webp", alt = "Ein Bild"),
            GuideBlock.ListBlock(
                ordered = false,
                items = listOf(listOf(Span("eins")), listOf(Span("zwei", bold = true))),
            ),
            GuideBlock.ListBlock(ordered = true, items = listOf(listOf(Span("erstens")), listOf(Span("zweitens")))),
        ),
    )

    @Test
    fun aDocumentIsWrittenAsMarkdown() {
        assertThat(GuideMarkdown.write(document)).isEqualTo(
            """
            ## Was ist ein erfundener Tee?

            Ein **kräftiger** und *milder* Tee
            mit [Link](https://shop.example/tee-info/erfundener-guide) und [**fettem Link**](umamit://catalog/XY0100).

            ![Ein Bild](g_0123abcd.webp)

            - eins
            - **zwei**

            1. erstens
            2. zweitens
            """.trimIndent(),
        )
    }

    @Test
    fun whatIsWrittenReadsBackTheSame() {
        assertThat(GuideMarkdown.read(GuideMarkdown.write(document))).isEqualTo(document)
    }

    @Test
    fun markdownCharactersInTheTextStayText() {
        val tricky = GuideDocument(
            listOf(
                GuideBlock.Paragraph(listOf(Span("# keine Überschrift"), LineBreak, Span("- keine Liste"))),
                GuideBlock.Paragraph(listOf(Span("1. kein Punkt, *kein* [Link](x) \\ ! _"))),
                GuideBlock.Paragraph(listOf(Span("![kein Bild](y)"))),
            ),
        )

        assertThat(GuideMarkdown.read(GuideMarkdown.write(tricky))).isEqualTo(tricky)
    }

    @Test
    fun aTargetsSpacesAndParenthesesAreEncoded() {
        val link =
            GuideDocument(listOf(GuideBlock.Paragraph(listOf(Span("Klammern", link = "https://shop.example/a (b)")))))

        val markdown = GuideMarkdown.write(link)

        assertThat(markdown).isEqualTo("[Klammern](https://shop.example/a%20%28b%29)")
        assertThat(GuideMarkdown.read(markdown).blocks)
            .containsExactly(GuideBlock.Paragraph(listOf(Span("Klammern", link = "https://shop.example/a%20%28b%29"))))
    }

    @Test
    fun adjacentSpansOfOneStyleAreMergedAndEmptyOnesDropped() {
        val split = GuideDocument(
            listOf(GuideBlock.Paragraph(listOf(Span("Grün"), Span(""), Span("tee"), Span(" ", bold = true)))),
        )

        assertThat(GuideMarkdown.read(GuideMarkdown.write(split)).blocks)
            .containsExactly(GuideBlock.Paragraph(listOf(Span("Grüntee"), Span(" ", bold = true))))
    }

    @Test
    fun anEmptyOrBrokenBodyReadsAsWhatItCan() {
        assertThat(GuideMarkdown.read("").blocks).isEmpty()
        assertThat(GuideMarkdown.read("**offen\n\n[ohne Ziel").blocks).containsExactly(
            GuideBlock.Paragraph(listOf(Span("offen", bold = true))),
            GuideBlock.Paragraph(listOf(Span("[ohne Ziel"))),
        ).inOrder()
    }
}
