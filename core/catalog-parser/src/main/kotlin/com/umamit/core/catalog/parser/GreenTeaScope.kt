package com.umamit.core.catalog.parser

/**
 * The catalog's scope by breadcrumb (08 §2.2, §2.4 item 3): products under `/tee/gruener-tee/`,
 * under `/tee/japantee/<type>/` for the green types only, or under `/matcha/matcha-teepulver/`.
 * The *Sorte* is the breadcrumb's type level (08 §2.2's Japan types and matcha powder).
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

    private const val GREEN_SECTION = "/tee/gruener-tee/"
    private const val MATCHA_POWDER = "/matcha/matcha-teepulver/"
    private val JAPAN_TEA = listOf("tee", "japantee")

    fun scope(breadcrumb: List<Crumb>): CatalogScope = when {
        breadcrumb.isEmpty() -> CatalogScope.UNKNOWN
        breadcrumb.any { isGreen(it.path) } -> CatalogScope.GREEN
        else -> CatalogScope.NOT_GREEN
    }

    /** The title of the deepest breadcrumb item that is a green type, e.g. „Sencha"; null without one. */
    fun teaType(breadcrumb: List<Crumb>): String? =
        breadcrumb.lastOrNull { isGreen(it.path) && isType(segments(it.path).lastOrNull()) }?.title

    private fun isGreen(path: String): Boolean {
        val normal = normalised(path)
        if (normal.startsWith(GREEN_SECTION) || normal.startsWith(MATCHA_POWDER)) return true
        val segments = segments(normal)
        return segments.size > JAPAN_TEA.size && segments.take(JAPAN_TEA.size) == JAPAN_TEA && isType(segments[2])
    }

    private fun isType(segment: String?) =
        segment != null && (segment in GREEN_TYPES || SHINCHA_OF_A_YEAR.matches(segment))

    private fun normalised(path: String) = path.lowercase().let { if (it.endsWith("/")) it else "$it/" }

    private fun segments(path: String) = normalised(path).split('/').filter { it.isNotEmpty() }
}
