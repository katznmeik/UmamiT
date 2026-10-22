package com.umamit.core.network

import okhttp3.HttpUrl

/**
 * The shop's robots.txt rules for UmamiT (RFC 9309; 08 §2.8, D-050, CLAUDE.md rule 7). A
 * disallowed URL is never fetched.
 *
 * - The group whose `User-agent` product token equals [PRODUCT_TOKEN] (case-insensitive; a
 *   version such as `UmamiT/1.0` is ignored) applies, else the `*` group, else nothing is
 *   disallowed; several matching groups are combined.
 * - The longest matching `Allow`/`Disallow` pattern wins; on a tie `Allow` wins.
 * - Patterns support `*` (any characters) and a trailing `$` (end of the path). Matching is a
 *   backtracking-free wildcard scan, so a hostile pattern cannot hang the app.
 * - Only the first [MAX_BYTES] are read (RFC 9309 §2.5); a leading BOM is ignored.
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
            .maxWithOrNull(compareBy<Rule> { it.length }.thenBy { it.allow })
        return match?.allow ?: true
    }

    private class Rule(pattern: String, val allow: Boolean) {
        /** The pattern's length for longest-match precedence. */
        val length = pattern.length
        private val anchored = pattern.endsWith('$')

        /** Without the `$`, runs of `*` collapsed; unanchored patterns match any continuation. */
        private val glob = (if (anchored) pattern.dropLast(1) else "$pattern*").replace(STAR_RUN, "*")

        fun matches(path: String) = wildcardMatches(glob, path)
    }

    companion object {
        /** The product token matched against `User-agent` lines; the User-Agent starts with it. */
        const val PRODUCT_TOKEN = "UmamiT"

        /** The robots.txt path on the shop host. */
        const val ROBOTS_PATH = "/robots.txt"

        /** Only this much of a robots.txt is parsed (RFC 9309 §2.5 allows ≥ 500 KiB). */
        const val MAX_BYTES = 512 * 1024

        private val STAR_RUN = Regex("\\*{2,}")
        private const val BOM = 0xFEFF.toChar()

        /** For an unreachable robots.txt (RFC 9309 §2.3.1.4): assume complete disallow. */
        val DISALLOW_ALL = RobotsTxt(listOf(Rule("/", allow = false)))

        /** For a missing robots.txt (404/410, RFC 9309 §2.3.1.3): everything may be fetched. */
        val ALLOW_ALL = RobotsTxt(emptyList())

        /** Parses the first [MAX_BYTES] of [bytes] as UTF-8. */
        fun parse(bytes: ByteArray): RobotsTxt = parse(bytes.decodeToString(0, minOf(bytes.size, MAX_BYTES)))

        /** Parses [text] for [PRODUCT_TOKEN]. Unknown lines (`Sitemap`, `Crawl-delay`, …) are ignored. */
        fun parse(text: String): RobotsTxt {
            val groups = mutableListOf<Pair<MutableSet<String>, MutableList<Rule>>>()
            var collectingAgents = false
            text.take(MAX_BYTES).removePrefix(BOM.toString()).lineSequence().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                val field = line.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
                val value = line.substringAfter(':', missingDelimiterValue = "").trim()
                when (field) {
                    "user-agent" -> {
                        if (!collectingAgents) groups += mutableSetOf<String>() to mutableListOf()
                        groups.last().first += value.substringBefore('/').trim().lowercase()
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

/**
 * Whether [glob] (with `*` wildcards) matches all of [text]: the classic two-pointer scan that
 * only ever resumes after the last `*`, so it needs at most |glob| × |text| steps and never
 * backtracks exponentially.
 */
internal fun wildcardMatches(glob: String, text: String): Boolean {
    var g = 0
    var t = 0
    var star = -1
    var resume = 0
    while (t < text.length) {
        when {
            g < glob.length && glob[g] == '*' -> {
                star = g++
                resume = t
            }

            g < glob.length && glob[g] == text[t] -> {
                g++
                t++
            }

            star >= 0 -> {
                g = star + 1
                t = ++resume
            }

            else -> return false
        }
    }
    while (g < glob.length && glob[g] == '*') g++
    return g == glob.length
}
