package com.umamit.core.catalog.parser

import com.umamit.core.model.HarvestSeason
import kotlin.math.roundToInt

/**
 * The conversion of a catalog tea's texts into collection-tea fields when the tea is added
 * (CAT-12; 08 §2.3, second table): the harvest year and season, the shading days and the
 * origin's country code. A value the texts don't give stays null. Never throws.
 */
object CollectionFields {
    private const val FIRST_HARVEST_YEAR = 2000
    private const val DAYS_PER_WEEK = 7
    private const val MAX_SHADING_DAYS = 60

    private val YEAR = Regex("""(?<!\d)(\d{4})(?!\d)""")

    /** Flush words the badge and the prose share; checked in this order. */
    private val FLUSH_WORDS: List<Pair<Regex, HarvestSeason>> = listOf(
        "first\\s*flush|frühlingsernte|erste\\s+pflückung|ichibancha|shincha" to HarvestSeason.FIRST_FLUSH,
        "second\\s*flush|nibancha" to HarvestSeason.SECOND_FLUSH,
        "sommerernte" to HarvestSeason.SUMMER,
        "herbsternte" to HarvestSeason.AUTUMN,
    ).map { (pattern, season) -> Regex(pattern, RegexOption.IGNORE_CASE) to season }

    /** A bare season names the harvest only in the *Ernte* badge, never in the prose („im Sommer kalt aufgießen"). */
    private val BADGE_SEASONS: List<Pair<Regex, HarvestSeason>> = listOf(
        "sommer" to HarvestSeason.SUMMER,
        "herbst" to HarvestSeason.AUTUMN,
    ).map { (pattern, season) -> Regex(pattern, RegexOption.IGNORE_CASE) to season }

    /** „6 Tage", „2 Wochen", „14-21 Tage", „3 bis 4 Wochen". */
    private val DURATION = Regex(
        """(\d+(?:,\d+)?)(?:\s*(?:-|–|bis)\s*(\d+(?:,\d+)?))?\s*(Tag|Woche)""",
        RegexOption.IGNORE_CASE,
    )
    private val SHADED = Regex("beschatt", RegexOption.IGNORE_CASE)
    private val SENTENCE_END = Regex("""(?<=[.!?])\s+|\n+""")
    private val WEEKS = Regex("woche", RegexOption.IGNORE_CASE)

    /** The shop's country names (*Anbaugebiete*) and their ISO 3166-1 alpha-2 codes (05 §3.1). */
    private val COUNTRIES = mapOf(
        "japan" to "JP",
        "china" to "CN",
        "taiwan" to "TW",
        "südkorea" to "KR",
        "korea" to "KR",
        "indien" to "IN",
        "nepal" to "NP",
        "vietnam" to "VN",
        "sri lanka" to "LK",
        "indonesien" to "ID",
        "thailand" to "TH",
        "laos" to "LA",
        "georgien" to "GE",
        "kenia" to "KE",
        "malawi" to "MW",
        "ruanda" to "RW",
    )

    /**
     * The first year from [FIRST_HARVEST_YEAR] to [currentYear] + 1 in [sources] (the *Ernte*
     * badge, the title, the prose, in that order), or null.
     */
    fun harvestYear(currentYear: Int, vararg sources: String?): Int? = sources.asSequence()
        .filterNotNull()
        .flatMap { text -> YEAR.findAll(text).map { it.value.toInt() } }
        .firstOrNull { it in FIRST_HARVEST_YEAR..currentYear + 1 }

    /**
     * The season of the *Ernte* [badge] (a flush word or a season; a badge naming neither is
     * [HarvestSeason.UNKNOWN]), else of the [prose]'s flush words; null when neither says.
     * Never taken from *Saison*, which is when to drink the tea.
     */
    fun harvestSeason(badge: String?, prose: String?): HarvestSeason? {
        if (!badge.isNullOrBlank()) {
            return (FLUSH_WORDS + BADGE_SEASONS).firstOrNull { (pattern, _) -> pattern.containsMatchIn(badge) }?.second
                ?: HarvestSeason.UNKNOWN
        }
        return prose?.let { text -> FLUSH_WORDS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second }
    }

    /**
     * The shading days of the *Beschattung* [badge], else of a sentence of the [prose] about
     * shading: a number of days or weeks (× 7), a range at its midpoint; null without a number
     * or beyond 60 days.
     */
    fun shadingDays(badge: String?, prose: String?): Int? {
        val text = if (!badge.isNullOrBlank()) {
            badge
        } else {
            prose?.split(SENTENCE_END)?.firstOrNull { SHADED.containsMatchIn(it) && DURATION.containsMatchIn(it) }
        }
        val match = text?.let(DURATION::find) ?: return null
        val from = match.groupValues[1].number()
        val to = match.groupValues[2].takeIf { it.isNotEmpty() }?.number() ?: from
        val unit = if (WEEKS.containsMatchIn(match.groupValues[3])) DAYS_PER_WEEK else 1
        return ((from + to) / 2 * unit).roundToInt().takeIf { it in 0..MAX_SHADING_DAYS }
    }

    /** The ISO code of the first country of [originCountry] („Japan", „Japan, China") the app knows, else null. */
    fun countryCode(originCountry: String?): String? = originCountry
        ?.split(',', '/', ';')
        ?.firstNotNullOfOrNull { COUNTRIES[it.trim().lowercase()] }

    private fun String.number(): Double = replace(',', '.').toDouble()
}
