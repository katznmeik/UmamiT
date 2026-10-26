package com.umamit.core.domain.guide

import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline

/**
 * A guide's body as Markdown (`catalog_guide.body_markdown`, 05 §4.3, CAT-09, D-087): the small
 * subset the converted articles need, written and read by the app itself.
 *
 * - Blocks are separated by a blank line: `## heading`, `![alt](target)` (an image alone in its
 *   block), lists (`- item` or `1. item`, one line per item) and paragraphs.
 * - Inside a paragraph a single line break is a line break.
 * - `**` switches bold, `*` italics, `***` both; `[text](target)` is a link. A target's
 *   spaces and parentheses are percent-encoded.
 * - `\` escapes the next character; the writer escapes `\`, `*`, `[`, `]` and `!` everywhere and
 *   a `#`, `-` or numbered-list dot at the start of a line.
 *
 * What the reader cannot make sense of stays text; a style left open ends with its block.
 */
object GuideMarkdown {
    private const val HEADING = "## "
    private const val BULLET = "- "
    private val NUMBERED = Regex("""^(\d+)\. """)
    private val IMAGE = Regex("""^!\[((?:\\.|[^\\\]])*)]\(([^()\s]*)\)$""")
    private val BLANK_LINES = Regex("""\n[ \t]*\n+""")
    private val ESCAPED = setOf('\\', '*', '[', ']', '!')

    fun write(document: GuideDocument): String =
        document.blocks.mapNotNull(::block).filter { it.isNotBlank() }.joinToString("\n\n")

    fun read(markdown: String): GuideDocument = GuideDocument(
        markdown.replace("\r\n", "\n").trim('\n').split(BLANK_LINES).filter { it.isNotBlank() }.mapNotNull(::readBlock),
    )

    // --- writing ---

    private fun block(block: GuideBlock): String? = when (block) {
        is GuideBlock.Heading -> block.text.oneLine().takeIf { it.isNotBlank() }?.let { HEADING + escape(it, true) }

        is GuideBlock.Paragraph -> inlines(block.inlines)

        is GuideBlock.Image -> "![${escape(block.alt.oneLine(), false)}](${target(block.source)})"

        is GuideBlock.ListBlock -> block.items.map { inlines(it.map(::withoutBreak)) }
            .filter { it.isNotBlank() }
            .mapIndexed { index, item -> if (block.ordered) "${index + 1}. $item" else BULLET + item }
            .joinToString("\n")
    }

    private fun withoutBreak(inline: GuideInline): GuideInline =
        if (inline is GuideInline.LineBreak) GuideInline.Span(" ") else inline

    /** Styled text: style switches only where the style changes, each line's start escaped. */
    private fun inlines(inlines: List<GuideInline>): String {
        val out = StringBuilder()
        var bold = false
        var italic = false
        var link: String? = null
        fun switchStyle(toBold: Boolean, toItalic: Boolean) {
            if (bold != toBold) out.append("**")
            if (italic != toItalic) out.append("*")
            bold = toBold
            italic = toItalic
        }
        for (inline in trimmedBreaks(mergedInlines(inlines))) {
            when (inline) {
                GuideInline.LineBreak -> {
                    switchStyle(toBold = false, toItalic = false)
                    link?.let { out.append("](").append(target(it)).append(")") }
                    link = null
                    out.append('\n')
                }

                is GuideInline.Span -> {
                    if (inline.link != link) {
                        switchStyle(toBold = false, toItalic = false)
                        link?.let { out.append("](").append(target(it)).append(")") }
                        if (inline.link != null) out.append('[')
                        link = inline.link
                    }
                    switchStyle(inline.bold, inline.italic)
                    // Only text that starts a line could be read as a heading, list item or image.
                    out.append(escape(inline.text.oneLine(), atLineStart = out.isEmpty() || out.endsWith('\n')))
                }
            }
        }
        switchStyle(toBold = false, toItalic = false)
        link?.let { out.append("](").append(target(it)).append(")") }
        return out.toString()
    }

    private fun escape(text: String, atLineStart: Boolean): String {
        val out = StringBuilder()
        text.forEach { char -> if (char in ESCAPED) out.append('\\').append(char) else out.append(char) }
        if (!atLineStart) return out.toString()
        return when {
            out.startsWith("#") || out.startsWith("-") -> "\\" + out

            else -> NUMBERED.find(out)?.let { match -> out.replaceRange(match.range, "${match.groupValues[1]}\\. ") }
                ?.toString()
                ?: out.toString()
        }
    }

    private fun target(url: String): String = url.oneLine().trim()
        .replace(" ", "%20").replace("(", "%28").replace(")", "%29").replace("\t", "%09")

    private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ')

    // --- reading ---

    private fun readBlock(text: String): GuideBlock? {
        val lines = text.lines().filter { it.isNotEmpty() }
        val first = lines.first()
        return when {
            lines.size == 1 && first.startsWith(
                HEADING,
            ) -> GuideBlock.Heading(unescapeGuideText(first.removePrefix(HEADING)).trim())

            lines.size == 1 && IMAGE.matches(first) -> IMAGE.matchEntire(first)!!.let { match ->
                GuideBlock.Image(source = match.groupValues[2], alt = unescapeGuideText(match.groupValues[1]))
            }

            lines.all { it.startsWith(BULLET) } -> GuideBlock.ListBlock(
                ordered = false,
                items = lines.map { GuideInlineReader(it.removePrefix(BULLET)).read() },
            )

            lines.all { NUMBERED.containsMatchIn(it) } -> GuideBlock.ListBlock(
                ordered = true,
                items = lines.map { GuideInlineReader(it.replaceFirst(NUMBERED, "")).read() },
            )

            else -> GuideBlock.Paragraph(GuideInlineReader(lines.joinToString("\n")).read()).takeIf {
                it.inlines.isNotEmpty()
            }
        }
    }

    /** No line break at the start or end, and never two in a row (that would end the paragraph). */
    private fun trimmedBreaks(inlines: List<GuideInline>): List<GuideInline> = inlines
        .filterIndexed { index, inline ->
            !(
                inline is GuideInline.LineBreak &&
                    inlines.getOrNull(index - 1) is GuideInline.LineBreak
                )
        }
        .dropWhile { it is GuideInline.LineBreak }
        .dropLastWhile { it is GuideInline.LineBreak }
}
