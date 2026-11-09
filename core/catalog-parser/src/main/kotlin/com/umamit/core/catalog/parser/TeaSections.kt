package com.umamit.core.catalog.parser

import com.umamit.core.model.CatalogKind

/**
 * One of the shop's tea sections beyond the green ones (08 §2.2, §2.4 item 3): the Kräutertees
 * from v0.2.1 (D-093), the black teas from v0.5.0 (D-108, parser v8), the white teas, oolongs and
 * Pu-Erhs with parser v9 (#205). A product with a crumb in a section is a
 * tea of it, whatever format or maker section it is also filed under (*Aufgussbeutel*, a maker's
 * page); the section of the deepest such crumb wins.
 *
 * A section that joins the catalog is a new entry in [TeaSections.ALL]: its paths, the parser
 * version that brought it, its kind and how it names its *Sorte* and style.
 *
 * @property roots the section's own path and those of parallel trees (the Japan tree's black teas),
 *   each with the *Sorte* a tea filed right there has, if any.
 * @property since the first parser version that keeps its teas (05 §4.4): a catalog last refreshed
 *   by an older parser has the section still to come (SCR-19's hint, 05 §9).
 * @property kind the catalog kind its teas show under (CAT-10); its styles are the [style]'s keys.
 * @property scope the scope its teas are kept under.
 * @property everySubSectionIsASorte every sub-section's title is a *Sorte* (the Kräutertees'); else
 *   only one that names a kind of tea is, and „Bio", „klassisch", „aromatisiert", the blends and
 *   „andere Regionen" leave it to the name ([namedSorten]).
 * @property namedSorten name keywords and the *Sorte* each gives, the first that matches.
 * @property style the style key of a tea by its name and *Sorte* (`text`) and whether it is flavoured.
 */
internal data class TeaSection(
    val roots: List<Root>,
    val since: Int,
    val kind: CatalogKind,
    val scope: CatalogScope = CatalogScope.TEA,
    val everySubSectionIsASorte: Boolean = false,
    val namedSorten: List<Pair<Regex, String>> = emptyList(),
    val style: (text: String, flavoured: Boolean) -> String,
) {
    /** A path of the section and the *Sorte* of a tea filed at it, if any. */
    data class Root(val path: String, val sorte: String? = null)

    /** The root [path] lies under; null when it lies under none. */
    fun rootOf(path: String): Root? =
        normalised(path).let { normal -> roots.firstOrNull { normal.startsWith(it.path) } }

    /**
     * A tea's *Sorte* by its [breadcrumb] and [name]: the title of the deepest sub-section that is one,
     * else a root's own *Sorte*, else the first of [namedSorten] its name names; null without one.
     */
    fun sorte(breadcrumb: List<Crumb>, name: String?): String? {
        val inSection = breadcrumb.filter { rootOf(it.path) != null }
        val bySubSection = inSection.lastOrNull { crumb ->
            val normal = normalised(crumb.path)
            roots.none { it.path == normal } &&
                (everySubSectionIsASorte || !NOT_A_KIND.containsMatchIn(normal.trimEnd('/').substringAfterLast('/')))
        }?.title
        return bySubSection
            ?: inSection.firstNotNullOfOrNull { crumb ->
                roots.firstOrNull { it.path == normalised(crumb.path) }?.sorte
            }
            ?: name?.let { text -> namedSorten.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second }
    }

    private companion object {
        /**
         * Sub-sections that name no kind of tea, by their last path segment (D-108): from #205 also a
         * tea master's or maker's page (`tee-von-meister-…`, `pu-erh-von-…`) and the loose Pu-Erhs.
         */
        val NOT_A_KIND = Regex("""^(bio|klassisch|aromatisiert)|blend|andere-regionen|(^|-)von-|^loose|loser""")
    }
}

/** The tea sections of the catalog beyond the green ones (08 §2.4 item 3). */
internal object TeaSections {
    private const val FLAVORED_BLACK = "flavored_black"
    private const val BLACK_GENERIC = "black_generic"

    /** Black-tea regions in a name or *Sorte*, the more specific first; each its style and *Sorte*. */
    private val BLACK_REGIONS: List<Triple<Regex, String?, String>> = listOf(
        Triple("darjeeling", "darjeeling", "Darjeeling"),
        Triple("assam", "assam", "Assam"),
        Triple("ceylon", "ceylon", "Ceylon"),
        Triple("nepal", null, "Nepal"),
    ).map { (pattern, style, sorte) -> Triple(Regex(pattern, RegexOption.IGNORE_CASE), style, sorte) }

    /** The Kräutertee section (v0.2.1, parser v7, D-093): every sub-section is a *Sorte*; one style. */
    val HERBAL = TeaSection(
        roots = listOf(TeaSection.Root("/tee/kraeutertee/")),
        since = 7,
        kind = CatalogKind.HERBAL,
        scope = CatalogScope.HERBAL,
        everySubSectionIsASorte = true,
        style = { _, _ -> "herbal" },
    )

    /**
     * The black teas (v0.5.0, parser v8, D-108): `/tee/schwarzer-tee/` and the Japan tree's black
     * teas (`/tee/japantee/koucha-schwarztee/`, *Sorte* „Japan"). A flavoured one is `flavored_black`,
     * else its region's style (Darjeeling, Assam, Ceylon), else `black_generic`.
     */
    val BLACK = TeaSection(
        roots = listOf(
            TeaSection.Root("/tee/schwarzer-tee/"),
            TeaSection.Root("/tee/japantee/koucha-schwarztee/", sorte = "Japan"),
        ),
        since = 8,
        kind = CatalogKind.BLACK,
        namedSorten = BLACK_REGIONS.map { (pattern, _, sorte) -> pattern to sorte },
        style = { text, flavoured ->
            if (flavoured) {
                FLAVORED_BLACK
            } else {
                BLACK_REGIONS.firstOrNull { (pattern, _, _) -> pattern.containsMatchIn(text) }?.second ?: BLACK_GENERIC
            }
        },
    )

    private const val WHITE_GENERIC = "white_generic"
    private const val FLAVORED_WHITE = "flavored_white"
    private const val OOLONG_GENERIC = "oolong_generic"
    private const val FLAVORED_OOLONG = "flavored_oolong"
    private const val DARK_GENERIC = "dark_generic"

    /** The first parser version of the white teas, oolongs and Pu-Erhs (#205). */
    private const val V9 = 9

    /** The Japan tree's section of another tea (`/tee/japantee/oolong/`): its teas' *Sorte* is „Japan". */
    private fun japan(segment: String) = TeaSection.Root("/tee/japantee/$segment/", sorte = "Japan")

    private fun named(vararg sorten: Pair<String, String>) =
        sorten.map { (pattern, sorte) -> Regex(pattern, RegexOption.IGNORE_CASE) to sorte }

    /**
     * The white teas (v0.5.0, parser v9, #205): `/tee/weisser-tee/` and the Japan tree's `weisstee/`. Its
     * sub-sections (Bio, klassisch, aromatisiert) name no kind, so the *Sorte* is the white kind its name
     * names; a flavoured one is `flavored_white`, else `white_generic`.
     */
    val WHITE = TeaSection(
        roots = listOf(TeaSection.Root("/tee/weisser-tee/"), japan("weisstee")),
        since = V9,
        kind = CatalogKind.WHITE,
        namedSorten = named(
            """bai\s*mu\s*dan|pai\s*mu\s*tan""" to "Bai Mu Dan",
            """silver\s*needle|yin\s*zhen""" to "Silver Needle",
            """shou\s*mei""" to "Shou Mei",
        ),
        style = { _, flavoured -> if (flavoured) FLAVORED_WHITE else WHITE_GENERIC },
    )

    /**
     * The oolongs (v0.5.0, parser v9, #205): `/tee/oolong/` and the Japan tree's `oolong/`. The Taiwan and
     * China sub-sections are the *Sorte*; Bio, klassisch, aromatisiert, andere Regionen and the tea master's
     * page leave it to the name („Taiwan", „Formosa" → Taiwan; „China" → China). A flavoured one is
     * `flavored_oolong`, else `oolong_generic`.
     */
    val OOLONG = TeaSection(
        roots = listOf(TeaSection.Root("/tee/oolong/"), japan("oolong")),
        since = V9,
        kind = CatalogKind.OOLONG,
        namedSorten = named("taiwan|formosa" to "Taiwan", "china" to "China"),
        style = { _, flavoured -> if (flavoured) FLAVORED_OOLONG else OOLONG_GENERIC },
    )

    /** „Sheng" and „Shou" as words: „Yesheng" (wild) is neither. */
    private val SHENG = Regex("""\bsheng\b""", RegexOption.IGNORE_CASE)
    private val SHOU = Regex("""\bshou\b""", RegexOption.IGNORE_CASE)

    /**
     * The Pu-Erhs (v0.5.0, parser v9, #205): `/tee/pu-erh/`, category `DARK`. The Sheng, Shou and Aged
     * sub-sections are the *Sorte*; the loose Pu-Erhs and the makers' pages leave it to the name (Sheng,
     * Shou). Style `sheng` or `shou` by the *Sorte* or name, else `dark_generic`.
     */
    val DARK = TeaSection(
        roots = listOf(TeaSection.Root("/tee/pu-erh/")),
        since = V9,
        kind = CatalogKind.DARK,
        namedSorten = listOf(SHENG to "Sheng", SHOU to "Shou"),
        style = { text, _ ->
            when {
                SHENG.containsMatchIn(text) -> "sheng"
                SHOU.containsMatchIn(text) -> "shou"
                else -> DARK_GENERIC
            }
        },
    )

    /** Every section, in the order of their [TeaSection.since]. */
    val ALL: List<TeaSection> = listOf(HERBAL, BLACK, WHITE, OOLONG, DARK)

    /** The section [path] lies in; null for none. */
    fun of(path: String): TeaSection? = ALL.firstOrNull { it.rootOf(path) != null }

    /** The section of [breadcrumb]'s deepest crumb in one; null for none. */
    fun of(breadcrumb: List<Crumb>): TeaSection? = breadcrumb.asReversed().firstNotNullOfOrNull { of(it.path) }

    /** The path of [breadcrumb]'s deepest crumb in a section; null for none. */
    fun pathIn(breadcrumb: List<Crumb>): String? = breadcrumb.lastOrNull { of(it.path) != null }?.path
}

private fun normalised(path: String) = path.lowercase().let { if (it.endsWith("/")) it else "$it/" }
