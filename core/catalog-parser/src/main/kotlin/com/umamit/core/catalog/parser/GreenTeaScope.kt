package com.umamit.core.catalog.parser

/**
 * The catalog's scope by breadcrumb (08 §2.2, §2.4 item 3): products under `/tee/gruener-tee/`,
 * under `/tee/japantee/<type>/` for the green types only, or under `/matcha/matcha-teepulver/`.
 * The *Sorte* is the breadcrumb's type level (08 §2.2's Japan types and matcha powder).
 *
 * A product of the Japan tree without a type level (a tea family's or a tea garden's page,
 * `/tee/japantee/teefamilien/<family>/`; live sample 2, D-089) is a green tea when its name names
 * a green type, or its badges say steamed or shaded, and neither its name nor its path names
 * another tea (Wakoucha, Oolong, …) or teaware; its *Sorte* is the green type its name names.
 */
internal object GreenTeaScope {
    /** The green types of the Japan trees (08 §2.2), as path segments. */
    private val GREEN_TYPES = setOf(
        "sencha",
        "shincha",
        "kabusecha",
        "gyokuro",
        "matcha",
        "kukicha",
        "kukicha-karigane-shiraore",
        "bancha",
        "houjicha",
        "kamairicha",
        "tamaryokucha",
        "genmaicha",
        "mizudashi-kaltaufgussbeutel",
        "matcha-teepulver",
    )
    private val SHINCHA_OF_A_YEAR = Regex("""shincha-\d{4}""")

    /**
     * The green types a name names and their *Sorte* as the shop titles its type pages, the more
     * specific first („Gyokuro Karigane" is a Kukicha, „Genmaicha mit Matcha" a Genmaicha).
     */
    private val NAMED_TYPES: List<Pair<Regex, String?>> = listOf(
        "genmaicha" to "Genmaicha",
        "houjicha|hojicha|hōjicha" to "Houjicha",
        "kukicha|karigane|shiraore" to "Kukicha",
        "kabuse" to "Kabusecha",
        "gyokuro" to "Gyokuro",
        "fukamushi" to "Sencha",
        "shincha" to "Shincha",
        "bancha" to "Bancha",
        "kamairicha" to "Kamairicha",
        "tamaryokucha" to "Tamaryokucha",
        "matcha" to "Matcha",
        "sencha" to "Sencha",
        // Green, but no type page of its own.
        "tencha|aracha|konacha|mizudashi|mizucha|grüntee|grüner\\s+tee|green\\s+tea" to null,
    ).map { (pattern, type) -> Regex(pattern, RegexOption.IGNORE_CASE) to type }

    /** Another tea or teaware in a name: never green, whatever else it names. */
    private val OTHER_IN_NAME = Regex(
        """koucha|oolong|schwarz(er)?\s*tee|black\s+tea|wei(ß|ss)(er)?\s*tee|white\s+tea|kräuter|kraeuter|""" +
            """kyusu|kanne|chawan|teeschale|becher|tasse|chasen|\bset\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Another tea or teaware as a path segment of the Japan tree (`/tee/japantee/wakoucha/`). */
    private val OTHER_IN_PATH =
        Regex("""koucha|oolong|schwarz(er)?-?tee|wei(ss|ß)(er)?-?tee|kraeuter|zubehoer|geschirr""")

    /**
     * A set in a name (D-091): „Set" as a word („3er Set", „Komplett-Set", „SET") or a set compound
     * („Probierset", „Geschenkset", „Jubiläumsset", …); „Setouchi" or „Sunset" are no sets.
     */
    private val SET_IN_NAME = Regex(
        """\bsets?\b|(probier|geschenk|starter|kennenlern|entdecker|jubiläums|jubilaeums|verkostungs|""" +
            """einsteiger|schnupper|tee|matcha)sets?\b""",
        RegexOption.IGNORE_CASE,
    )

    /** A set category as a path segment (`gruentee-sets`, `teesets`, `multipacks-sets-zum-probieren`). */
    private val SET_IN_PATH = Regex("""(^|-)(tee)?sets?(-|$)""")

    private const val GREEN_SECTION = "/tee/gruener-tee/"
    private const val MATCHA_POWDER = "/matcha/matcha-teepulver/"
    private val JAPAN_TEA = listOf("tee", "japantee")

    /**
     * The scope of a product with [breadcrumb] and [name]; [greenBadge] is a steaming or shading
     * badge, which only green teas carry. A product of the green sections whose name or
     * breadcrumb says set is a [CatalogScope.SET], even without a set number (D-091).
     */
    fun scope(breadcrumb: List<Crumb>, name: String? = null, greenBadge: Boolean = false): CatalogScope = when {
        breadcrumb.isEmpty() -> CatalogScope.UNKNOWN

        breadcrumb.none { isGreen(it.path) } && !(isJapanTeaOfNoType(breadcrumb) && saysGreen(name, greenBadge)) ->
            CatalogScope.NOT_GREEN

        isSet(breadcrumb, name) -> CatalogScope.SET

        else -> CatalogScope.GREEN
    }

    private fun isSet(breadcrumb: List<Crumb>, name: String?): Boolean =
        (name != null && SET_IN_NAME.containsMatchIn(name)) ||
            breadcrumb.any { crumb -> segments(crumb.path).any { SET_IN_PATH.containsMatchIn(it) } }

    /**
     * The title of the deepest breadcrumb item that is a green type, e.g. „Sencha"; for a Japan tea
     * without a type level the type its [name] names; null without one.
     */
    fun teaType(breadcrumb: List<Crumb>, name: String? = null): String? =
        breadcrumb.lastOrNull { isGreen(it.path) && isType(segments(it.path).lastOrNull()) }?.title
            ?: name?.takeIf { breadcrumb.none { isGreen(it.path) } && isJapanTeaOfNoType(breadcrumb) }
                ?.let(::namedType)

    private fun isGreen(path: String): Boolean {
        val normal = normalised(path)
        if (normal.startsWith(GREEN_SECTION) || normal.startsWith(MATCHA_POWDER)) return true
        val segments = segments(normal)
        return segments.size > JAPAN_TEA.size && segments.take(JAPAN_TEA.size) == JAPAN_TEA && isType(segments[2])
    }

    /** Below the Japan tree, without another tea's or teaware's segment. */
    private fun isJapanTeaOfNoType(breadcrumb: List<Crumb>): Boolean {
        val japan = breadcrumb.map { segments(it.path) }
            .filter { it.size > JAPAN_TEA.size && it.take(JAPAN_TEA.size) == JAPAN_TEA }
        return japan.isNotEmpty() && japan.flatten().none { OTHER_IN_PATH.containsMatchIn(it) }
    }

    private fun saysGreen(name: String?, greenBadge: Boolean): Boolean {
        if (name != null && OTHER_IN_NAME.containsMatchIn(name)) return false
        return greenBadge || (name != null && NAMED_TYPES.any { (pattern, _) -> pattern.containsMatchIn(name) })
    }

    private fun namedType(name: String): String? = if (OTHER_IN_NAME.containsMatchIn(name)) {
        null
    } else {
        NAMED_TYPES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(name) }?.second
    }

    private fun isType(segment: String?) =
        segment != null && (segment in GREEN_TYPES || SHINCHA_OF_A_YEAR.matches(segment))

    private fun normalised(path: String) = path.lowercase().let { if (it.endsWith("/")) it else "$it/" }

    private fun segments(path: String) = normalised(path).split('/').filter { it.isNotEmpty() }
}
