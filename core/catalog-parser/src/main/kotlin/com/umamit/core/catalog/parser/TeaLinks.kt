package com.umamit.core.catalog.parser

/**
 * A link to one tea's product page that the user shared to the app or pasted (CAT-17 AC2,
 * 08 §2.4 item 9, D-094).
 *
 * @property path the page's path as given: `/<slug>/<article number>`, ASCII letters, digits and
 *   hyphens only. The app resolves it against the shop's own base URL, so the request can only go
 *   to the allow-listed host.
 * @property base the base article number (05 §4.1): the catalog key the page should show.
 */
data class TeaLink(val path: String, val base: String) {
    /** The link as the app reads it: always `https://` on the shop's host. */
    val url: String get() = "https://${TeaLinks.HOST}$path"
}

/** What [TeaLinks] made of a shared or pasted text. */
sealed interface TeaLinkResult {
    /** A tea link of the shop: the app may fetch its one page. */
    data class Accepted(val link: TeaLink) : TeaLinkResult

    /**
     * A product link of the shop whose number is a set or a sale duplicate (`…SET`, `…SALE`;
     * 08 §2.4 item 2, D-091): never a catalog tea, so nothing is fetched.
     */
    data object OutOfScope : TeaLinkResult

    /** Not a tea link of the shop: nothing is fetched. */
    data object Refused : TeaLinkResult
}

/**
 * Reads the text the user shares or pastes (CAT-17 AC2). The text is untrusted input and is
 * checked before any request: only a link `https://www.tee-kontor-kiel.de/<slug>/<article number>`
 * with an optional `-<grams>` size suffix is accepted.
 *
 * Strict on purpose (CLAUDE.md rule 7, D-094): ASCII only, so no letter that matches the host or
 * the scheme only when case is ignored (the Kelvin sign, a dotless ı, a long ſ); `https` only;
 * exactly the shop's host (scheme and host in any ASCII case), no port, no userinfo, no trailing
 * dot; a path of exactly two segments of ASCII letters, digits and hyphens, so no query, fragment,
 * percent-encoding, dot segment or trailing slash; not a `/tee-info/` guide; the second segment an
 * article number (08 §2.2) whose suffix, if any, is grams. Anything else, or anything in doubt,
 * is refused. A share may carry text around the link (a page title): the text is split at
 * whitespace, and exactly one link may be in it (the same link twice counts once). Lengths are
 * capped before anything is parsed.
 */
object TeaLinks {
    /** The shop's host, the only one the app talks to (GEN-01 AC1, `ShopClient.SHOP_HOST`). */
    const val HOST = "www.tee-kontor-kiel.de"

    /** The longest shared or pasted text read; a longer one is refused unread. */
    const val MAX_TEXT_LENGTH = 2_000

    /** The longest link accepted (the shop's product links are well under 150 characters). */
    const val MAX_LINK_LENGTH = 300

    private const val SCHEME = "https://"
    private const val ASCII_END = 0x80
    private const val MAX_SLUG = 200
    private const val MAX_NUMBER = 40
    private val WHITESPACE = Regex("""\s+""")
    private val PATH = Regex("""/([A-Za-z0-9-]{1,$MAX_SLUG})/([A-Za-z0-9-]{1,$MAX_NUMBER})""")
    private val GRAMS = Regex("""[1-9]\d{0,4}""")

    /** First path segments of the shop that are no product pages although a number may follow. */
    private val NOT_PRODUCTS = setOf("tee-info")

    /** The tea link in [text] (a share with a title around it, or a pasted link). */
    fun find(text: String): TeaLinkResult {
        if (text.length > MAX_TEXT_LENGTH) return TeaLinkResult.Refused
        val links = text.split(WHITESPACE).filter { "://" in it }.distinct()
        return links.singleOrNull()?.let(::parse) ?: TeaLinkResult.Refused
    }

    /** [link] alone, without any text around it. */
    fun parse(link: String): TeaLinkResult {
        val path = pathOnShop(link)
        val segments = path?.let(PATH::matchEntire)?.groupValues
        val slug = segments?.get(1)
        val number = segments?.get(2)?.let(ArticleNumber::parse)
        val grams = number?.variant
        return when {
            path == null || slug == null || number == null -> TeaLinkResult.Refused
            slug.lowercase() in NOT_PRODUCTS -> TeaLinkResult.Refused
            grams != null && !GRAMS.matches(grams) -> TeaLinkResult.Refused
            number.isSet || number.isSale -> TeaLinkResult.OutOfScope
            else -> TeaLinkResult.Accepted(TeaLink(path, number.base))
        }
    }

    /**
     * The path of [link] (from its first `/` on) when it is no longer than [MAX_LINK_LENGTH], is
     * ASCII only and starts with `https://` and exactly the shop's host: no userinfo (`@`), no port,
     * no trailing dot. ASCII is checked first because a case-insensitive comparison also matches
     * some non-ASCII letters (`K` U+212A equals `k`, `ı` equals `i`).
     */
    private fun pathOnShop(link: String): String? {
        val rest = link
            .takeIf { it.length <= MAX_LINK_LENGTH && it.all { char -> char.code < ASCII_END } }
            ?.takeIf { it.startsWith(SCHEME, ignoreCase = true) }
            ?.substring(SCHEME.length)
        val slash = rest?.indexOf('/') ?: -1
        val onShop = rest != null && slash >= 0 && rest.substring(0, slash).equals(HOST, ignoreCase = true)
        return if (onShop) rest?.substring(slash) else null
    }
}
