package com.umamit.core.catalog.parser

/**
 * A green type a tea's name names: its [pattern], the *Sorte* the shop titles its type page with
 * ([sorte], null for a green type without a page of its own) and its built-in style ([styleKey]).
 * [inScope] says whether the name makes a Japan tea of no type level green ([GreenTeaScope]); a
 * Chinese or Korean type only gives the style ([StyleMapper]).
 */
internal class GreenType(pattern: String, val sorte: String?, val styleKey: String?, val inScope: Boolean = true) {
    val pattern = Regex(pattern, RegexOption.IGNORE_CASE)
}

/**
 * The green types by name, the more specific first („Gyokuro Karigane" is a Kukicha, „Genmaicha mit
 * Matcha" a Genmaicha): the first match decides, for the *Sorte* and the style alike, so the order stays.
 */
internal val GREEN_TYPES_BY_NAME: List<GreenType> = listOf(
    GreenType("genmaicha", "Genmaicha", "genmaicha"),
    GreenType("houjicha|hojicha|hōjicha", "Houjicha", "hojicha"),
    GreenType("kukicha|karigane|shiraore", "Kukicha", "karigane"),
    GreenType("kabuse", "Kabusecha", "kabusecha"),
    GreenType("gyokuro", "Gyokuro", "gyokuro"),
    GreenType("fukamushi", "Sencha", "fukamushi_sencha"),
    GreenType("shincha", "Shincha", "shincha"),
    GreenType("bancha", "Bancha", "bancha"),
    GreenType("kamairicha", "Kamairicha", "kamairicha"),
    GreenType("tamaryokucha", "Tamaryokucha", "tamaryokucha"),
    GreenType("matcha", "Matcha", "matcha"),
    GreenType("sencha", "Sencha", "sencha"),
    // Green, but no type page and no style of its own.
    GreenType("tencha|aracha|konacha|mizudashi|mizucha|grüntee|grüner\\s+tee|green\\s+tea", null, null),
    GreenType("long\\s*jing|lung\\s*ching", null, "longjing", inScope = false),
    // The shop writes „Bi Lo Chun" and „Pi Lo Chun" (live sample 2, D-089).
    GreenType("[bp]i\\s*lu?o\\s*chun", null, "bi_luo_chun", inScope = false),
    GreenType("anji", null, "anji_bai_cha", inScope = false),
    // Korea's grades by picking time: Ujeon (Woojeon), Sejak, Jungjak (Joongjak), Daejak.
    GreenType("ujeon|woojeon|sejak|joongjak|jungjak|daejak", null, "korean_green", inScope = false),
)
