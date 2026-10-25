package com.umamit.core.domain.guide

import com.umamit.core.model.GuideInline

/** Reads one paragraph's or list item's styled text of [GuideMarkdown]'s subset. */
internal class GuideInlineReader(private val text: String) {
    private val result = mutableListOf<GuideInline>()
    private val pending = StringBuilder()
    private var bold = false
    private var italic = false
    private var link: String? = null

    /** Where the current link's text ends (its `]`) and its target. */
    private var linkEnd = -1
    private var linkTarget: String? = null

    fun read(): List<GuideInline> {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            index = when {
                char == '\\' && index + 1 < text.length -> {
                    pending.append(text[index + 1])
                    index + 2
                }

                index == linkEnd -> endLink(index)

                char == '*' -> switchStyle(index)

                char == '[' && link == null && linkAt(index) -> {
                    flush()
                    link = linkTarget
                    index + 1
                }

                char == '\n' -> {
                    flush()
                    result += GuideInline.LineBreak
                    index + 1
                }

                else -> {
                    pending.append(char)
                    index + 1
                }
            }
        }
        flush()
        return mergedInlines(result)
    }

    /** Ends the link at its `]` ([index]) and skips its `(target)`; returns where the text goes on. */
    private fun endLink(index: Int): Int {
        flush()
        val target = linkTarget.orEmpty()
        link = null
        linkEnd = -1
        return index + target.length + LINK_SYNTAX
    }

    /** A run of `*` from [start]: one switches italics, two bold, three both; returns the index after it. */
    private fun switchStyle(start: Int): Int {
        var index = start
        while (index < text.length && text[index] == '*') index++
        val run = index - start
        flush()
        if (run % 2 == 1) italic = !italic
        if (run / 2 % 2 == 1) bold = !bold
        return index
    }

    /** Whether a link starts at [start]: an unescaped `]` followed by `(target)` on the same line. */
    private fun linkAt(start: Int): Boolean {
        val end = textEnd(start + 1)
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val close = if (end < 0) -1 else text.indexOf(')', end)
        val found = end in 0 until lineEnd && end + 1 < text.length && text[end + 1] == '(' && close in 0 until lineEnd
        if (found) {
            linkEnd = end
            linkTarget = text.substring(end + 2, close)
        }
        return found
    }

    /** The first unescaped `]` from [from] on the same line, or -1. */
    private fun textEnd(from: Int): Int {
        var index = from
        while (index < text.length && text[index] != '\n' && text[index] != ']') {
            index += if (text[index] == '\\') 2 else 1
        }
        return if (index < text.length && text[index] == ']') index else -1
    }

    private fun flush() {
        if (pending.isEmpty()) return
        result += GuideInline.Span(pending.toString(), bold = bold, italic = italic, link = link)
        pending.clear()
    }

    private companion object {
        /** „](" and „)" around a link's target. */
        const val LINK_SYNTAX = 3
    }
}

/** [text] with its backslash escapes resolved. */
internal fun unescapeGuideText(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '\\' && index + 1 < text.length) {
            append(text[index + 1])
            index += 2
        } else {
            append(char)
            index++
        }
    }
}

/** Adjacent spans of one style and link merged, empty spans dropped. */
internal fun mergedInlines(inlines: List<GuideInline>): List<GuideInline> {
    val out = mutableListOf<GuideInline>()
    for (inline in inlines) {
        if (inline is GuideInline.Span && inline.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (inline is GuideInline.Span && last is GuideInline.Span && last.copy(text = "") == inline.copy(text = "")) {
            out[out.lastIndex] = last.copy(text = last.text + inline.text)
        } else {
            out += inline
        }
    }
    return out
}
