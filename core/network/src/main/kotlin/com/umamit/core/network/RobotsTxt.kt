package com.umamit.core.network

import okhttp3.HttpUrl

/**
 * The shop's robots.txt rules for UmamiT (RFC 9309; 08 §2.8, CLAUDE.md rule 7). A disallowed
 * URL is never fetched.
 *
 * - The group whose `User-agent` equals [PRODUCT_TOKEN] (case-insensitive) applies, else the
 *   `*` group, else nothing is disallowed; several matching groups are combined.
 * - The longest matching `Allow`/`Disallow` pattern wins; on a tie `Allow` wins.
 * - Patterns support `*` (any characters) and a trailing `$` (end of the path).
 * - `/robots.txt` itself is always allowed.
 *
 * Paths are compared in their encoded form, including the query (`/a/?p=2`).
 */
class RobotsTxt private constructor(private val rules: List<Rule>) {
    /** Whether [url]'s path and query may be fetched. */
    fun isAllowed(url: HttpUrl): Boolean = isAllowed(url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: ""))

    /** Whether [pathAndQuery] (starting with `/`) may be fetched. */
    fun isAllowed(pathAndQuery: String): Boolean {
        if (pathAndQuery == ROBOTS_PATH) return true
        val match = rules
            .filter { it.matches(pathAndQuery) }
            .maxWithOrNull(compareBy<Rule> { it.pattern.length }.thenBy { it.allow })
        return match?.allow ?: true
    }

    private class Rule(val pattern: String, val allow: Boolean) {
        private val regex = Regex(
            buildString {
                append('^')
                val anchored = pattern.endsWith('$')
                val body = if (anchored) pattern.dropLast(1) else pattern
                body.split('*').forEachIndexed { i, part ->
                    if (i > 0) append(".*")
                    append(Regex.escape(part))
                }
                if (anchored) append('$')
            },
        )

        fun matches(path: String) = regex.containsMatchIn(path)
    }

    companion object {
        /** The product token matched against `User-agent` lines; the User-Agent starts with it. */
        const val PRODUCT_TOKEN = "UmamiT"

        /** The robots.txt path on the shop host. */
        const val ROBOTS_PATH = "/robots.txt"

        /** For an unreachable robots.txt (5xx, RFC 9309 §2.3.1.4): assume complete disallow. */
        val DISALLOW_ALL = RobotsTxt(listOf(Rule("/", allow = false)))

        /** For a missing robots.txt (4xx, RFC 9309 §2.3.1.3): everything may be fetched. */
        val ALLOW_ALL = RobotsTxt(emptyList())

        /** Parses [text] for [PRODUCT_TOKEN]. Unknown lines (`Sitemap`, `Crawl-delay`, …) are ignored. */
        fun parse(text: String): RobotsTxt {
            val groups = mutableListOf<Pair<MutableSet<String>, MutableList<Rule>>>()
            var collectingAgents = false
            text.lineSequence().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                val field = line.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
                val value = line.substringAfter(':', missingDelimiterValue = "").trim()
                when (field) {
                    "user-agent" -> {
                        if (!collectingAgents) groups += mutableSetOf<String>() to mutableListOf()
                        groups.last().first += value.lowercase()
                        collectingAgents = true
                    }

                    "allow", "disallow" -> {
                        collectingAgents = false
                        // Rules before any User-agent line belong to no group; an empty value matches nothing.
                        if (groups.isNotEmpty() && value.isNotEmpty()) {
                            groups.last().second += Rule(value, allow = field == "allow")
                        }
                    }
                }
            }
            val own = groups.filter { PRODUCT_TOKEN.lowercase() in it.first }
            val applicable = own.ifEmpty { groups.filter { "*" in it.first } }
            return RobotsTxt(applicable.flatMap { it.second })
        }
    }
}
