package com.umamit.core.model

/**
 * A catalog kind (v0.5.0, D-108, CAT-10): one of the ranges the catalog list shows apart, a chip of
 * its own in Sammlung › Katalog. A catalog tea's kind comes from its style (see [of]); a tea whose
 * style no kind claims belongs to [GREEN], the default kind.
 *
 * A new kind is a new entry in [ALL] (and a label in the UI): the browser, the filter and the
 * remembered folds work on the kind alone.
 *
 * @property key the kind's stable key: also the prefix of its keys in the stored folds (`catalogFolded`,
 *   05 §6), so the keys of the first two kinds never change.
 * @property styleKeys the keys of the styles whose teas are of this kind; empty for the default kind.
 */
data class CatalogKind(val key: String, val styleKeys: Set<String> = emptySet()) {
    companion object {
        /** The green teas, the default kind: every tea no other kind claims. */
        val GREEN = CatalogKind("green")

        /** The *Kräutertees* (v0.2.1, D-093): the teas of the built-in style `herbal`. */
        val HERBAL = CatalogKind("herbal", setOf("herbal"))

        /** The kinds in the order of the chips; [GREEN] first. */
        val ALL: List<CatalogKind> = listOf(GREEN, HERBAL)

        /** The kind of a tea of style [styleKey] among [kinds]; [GREEN] when none claims it. */
        fun of(styleKey: String?, kinds: List<CatalogKind> = ALL): CatalogKind =
            kinds.firstOrNull { styleKey != null && styleKey in it.styleKeys } ?: GREEN
    }
}
