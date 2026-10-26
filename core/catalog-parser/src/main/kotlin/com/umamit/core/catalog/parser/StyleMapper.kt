package com.umamit.core.catalog.parser

import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.domain.style.TeaStyle
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel

/**
 * A catalog tea's built-in style (CAT-06, 08 §2.6).
 *
 * @property form loose leaf, powder (matcha, hōjicha powder) or tea bag (Mizudashi).
 * @property japanese a Japanese tea: its Kyusu row is its default recipe (08 §2.5 step 1). A
 *   flavoured sencha keeps it, though its style is `flavored_green`.
 * @property unmapped neither breadcrumb nor name named a style: [style] is `green_generic` and
 *   the tea is flagged `UNMAPPED_STYLE`.
 * @property coldBrewDefault a cold-brew bag (Mizudashi) or a Mizucha: its default recipe is the cold brew.
 */
data class StyleMatch(
    val style: TeaStyle,
    val form: TeaForm,
    val japanese: Boolean,
    val unmapped: Boolean,
    val coldBrewDefault: Boolean,
)

/** Maps a catalog tea to a built-in style (CAT-06): the breadcrumb's last segment, else name keywords (08 §2.6). */
object StyleMapper {
    /** Breadcrumb segments (the shop's type level) and their style. */
    private val SEGMENTS: List<Pair<Regex, String>> = listOf(
        Regex("gyokuro") to "gyokuro",
        Regex("kabusecha|kabuse") to "kabusecha",
        Regex("sencha") to "sencha",
        Regex("shincha(-\\d{4})?") to "shincha",
        Regex("matcha|matcha-teepulver") to "matcha",
        Regex("kukicha.*|karigane") to "karigane",
        Regex("bancha") to "bancha",
        Regex("houjicha|hojicha") to "hojicha",
        Regex("genmaicha") to "genmaicha",
        Regex("kamairicha") to "kamairicha",
        Regex("tamaryokucha") to "tamaryokucha",
    )

    /**
     * Name keywords, the more specific first: „Gyokuro Karigane" is a karigane, „Genmaicha mit
     * Matcha" a genmaicha.
     */
    private val KEYWORDS: List<Pair<Regex, String>> = listOf(
        "genmaicha" to "genmaicha",
        "houjicha|hojicha|hōjicha" to "hojicha",
        "kukicha|karigane|shiraore" to "karigane",
        "kabuse" to "kabusecha",
        "gyokuro" to "gyokuro",
        "fukamushi" to "fukamushi_sencha",
        "shincha" to "shincha",
        "bancha" to "bancha",
        "kamairicha" to "kamairicha",
        "tamaryokucha" to "tamaryokucha",
        "matcha" to "matcha",
        "sencha" to "sencha",
        "long\\s*jing|lung\\s*ching" to "longjing",
        // The shop writes „Bi Lo Chun" and „Pi Lo Chun" (live sample 2, D-089).
        "[bp]i\\s*lu?o\\s*chun" to "bi_luo_chun",
        "anji" to "anji_bai_cha",
        // Korea's grades by picking time: Ujeon (Woojeon), Sejak, Jungjak (Joongjak), Daejak.
        "ujeon|woojeon|sejak|joongjak|jungjak|daejak" to "korean_green",
    ).map { (pattern, key) -> Regex(pattern, RegexOption.IGNORE_CASE) to key }

    private const val MIZUDASHI = "mizudashi-kaltaufgussbeutel"
    private const val FLAVOURED_SECTION = "aromatisierter-gruentee"
    private val JAPAN_PATH = Regex("/(japan|japantee)/")
    private val POWDER = Regex("pulver|powder", RegexOption.IGNORE_CASE)

    /** A name saying cold-brew bags („Mizudashi", „Kaltaufgussbeutel"). */
    private val MIZUDASHI_NAME =
        Regex("""mizudashi|kaltaufgussbeutel|cold\s*-?\s*brew\s*-?\s*beutel""", RegexOption.IGNORE_CASE)

    /** „Mizucha", Japanese water tea: brewed cold (D-091). */
    private val MIZUCHA = Regex("mizucha", RegexOption.IGNORE_CASE)
    private const val HOJICHA = "hojicha"
    private const val HOJICHA_POWDER = "hojicha_powder"

    /** Tea bags, not a refill bag of leaf („Nachfüllbeutel"). */
    private val TEA_BAGS = Regex("aufgussbeutel|teebeutel|pyramidenbeutel", RegexOption.IGNORE_CASE)
    private val SENCHA_STYLES = setOf("sencha", "fukamushi_sencha")
    private val UMAMI =
        listOf(UmamiLevel.NONE, UmamiLevel.LOW, UmamiLevel.MEDIUM, UmamiLevel.HIGH, UmamiLevel.VERY_HIGH)

    /**
     * The style of a tea in the category [categoryPath] (`/tee/gruener-tee/japan/sencha/`) named
     * [name], with the shop's *Sorte* [teaType]. A flavoured sencha is `flavored_green`, and a
     * flavoured tea no keyword names is too (it is no unknown green tea); one from Korea
     * ([originCountry], *Anbaugebiete*) no keyword names is a Korean green.
     */
    fun map(
        categoryPath: String?,
        name: String?,
        teaType: String? = null,
        isFlavored: Boolean = false,
        originCountry: String? = null,
    ): StyleMatch {
        val segment = categoryPath?.trimEnd('/')?.substringAfterLast('/')?.lowercase().orEmpty()
        val text = listOfNotNull(name, teaType).joinToString(" ")
        val byName = KEYWORDS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
            ?: KOREAN_GREEN.takeIf { originCountry != null && KOREA.containsMatchIn(originCountry) }
        // Cold-brew bags by their section or, filed under a type, by their name (D-089).
        val mizudashi = segment == MIZUDASHI || MIZUDASHI_NAME.containsMatchIn(text)
        // „Mizucha" (water tea) brews cold by default too, bags or leaves (D-091).
        val coldBrew = mizudashi || MIZUCHA.containsMatchIn(text)
        val key = withPowder(styleKey(segment, byName, coldBrew), byName, text)
        val flavoured = isFlavored && (key in SENCHA_STYLES || key == null)
        val style = (if (flavoured) FLAVORED else key)?.let(BuiltInStyles::byKey) ?: BuiltInStyles.GENERIC
        // The base style or the name's (a flavoured sencha), or the Japan section, make a tea Japanese.
        val japanese = listOfNotNull(style.key, key, byName).any { BuiltInStyles.byKey(it)?.japanese == true } ||
            (categoryPath != null && JAPAN_PATH.containsMatchIn(categoryPath))
        return StyleMatch(
            style = style,
            form = form(mizudashi, style.key, text),
            japanese = japanese,
            unmapped = key == null && !flavoured,
            coldBrewDefault = coldBrew,
        )
    }

    /**
     * The style key by the breadcrumb's [segment], else the name's [byName]; null when neither
     * names one. A tea that brews cold by default ([coldBrew]) without either is the generic
     * style, not flagged (D-089).
     */
    private fun styleKey(segment: String, byName: String?, coldBrew: Boolean): String? {
        val bySegment = SEGMENTS.firstOrNull { (pattern, _) -> pattern.matches(segment) }?.second
        return when {
            segment == MIZUDASHI -> byName ?: BuiltInStyles.GENERIC.key
            coldBrew && bySegment == null && byName == null -> BuiltInStyles.GENERIC.key
            segment == FLAVOURED_SECTION -> if (byName == "matcha") byName else FLAVORED
            bySegment == "sencha" && byName == "fukamushi_sencha" -> byName
            else -> bySegment ?: byName
        }
    }

    /**
     * [key], or the hōjicha powder's style when [key] or the name ([byName]) says hōjicha and
     * [text] says powder: a powder is whisked, never steeped (08 §3, D-091), whatever the breadcrumb.
     */
    private fun withPowder(key: String?, byName: String?, text: String): String? =
        if ((key == HOJICHA || byName == HOJICHA) && POWDER.containsMatchIn(text)) HOJICHA_POWDER else key

    private fun form(mizudashi: Boolean, styleKey: String, text: String): TeaForm = when {
        mizudashi -> TeaForm.TEA_BAG
        styleKey == "matcha" || styleKey == HOJICHA_POWDER -> TeaForm.POWDER
        TEA_BAGS.containsMatchIn(text) -> TeaForm.TEA_BAG
        else -> TeaForm.LOOSE_LEAF
    }

    /** The umami level (08 §2.6): the shop's Umami scale (0 → NONE … 4–5 → VERY_HIGH), else the style's. */
    fun umamiLevel(scales: List<ShopScale>, style: TeaStyle): UmamiLevel =
        scales.firstOrNull { it.label.equals("Umami", ignoreCase = true) }
            ?.let { UMAMI[it.leaves.coerceIn(0, UMAMI.lastIndex)] }
            ?: style.umami

    private const val FLAVORED = "flavored_green"
    private const val KOREAN_GREEN = "korean_green"

    /** *Anbaugebiete* Korea: a green tea no keyword names is a Korean green (D-089). */
    private val KOREA = Regex("korea", RegexOption.IGNORE_CASE)
}
