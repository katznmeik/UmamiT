package com.umamit.feature.collection.receive

import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.Tea

/** How SCR-32 found the received recipe's tea (D-130 Q13): „gefunden über den Katalog" or „gleicher Name". */
enum class MatchKind { CATALOG, NAME }

/** The collection tea [teaId] a received recipe is for, found by [kind]. */
data class TeaMatch(val teaId: String, val kind: MatchKind)

/**
 * The same tea for a received recipe (PRF-08 AC6, D-130 Q13) and the name it takes over (AC7), as pure functions.
 */
object RecipeMatch {
    /** A recipe name's longest length (05 §3.2). */
    const val MAX_RECIPE_NAME = 40

    /**
     * The tea of [file] among [teas] (archived ones included): one that links to its `catalog_key` wins; else one that
     * links to [loadedKey], the key *Tee über Shop-Link laden* returned (it may be a merged tea's; PO, 2026-11-18);
     * else one with its name, ignoring case and accents. Among several, a tea that is not archived first, then the
     * first by name. Null when none matches. The match is only preselected; the user may pick another tea.
     */
    fun find(file: SharedTea, teas: List<Tea>, loadedKey: String? = null): TeaMatch? {
        val ordered = teas.sortedWith(compareBy<Tea> { it.isArchived }.thenBy { SearchQuery.normalized(it.name) })
        val byKey = listOfNotNull(file.catalogKey, loadedKey).firstNotNullOfOrNull { key ->
            ordered.firstOrNull { it.catalogKey == key }
        }
        if (byKey != null) return TeaMatch(byKey.id, MatchKind.CATALOG)
        val name = comparable(file.name)
        return ordered.firstOrNull { comparable(it.name) == name }?.let { TeaMatch(it.id, MatchKind.NAME) }
    }

    /**
     * The new recipe's name (PRF-08 AC7): [name] as in the file, or with „ (2)", „ (3)" … when the tea has one of
     * that name already (ignoring case), cut so the suffix fits [MAX_RECIPE_NAME].
     */
    fun recipeName(name: String, taken: Collection<String>): String {
        val names = taken.map { it.trim().lowercase() }.toSet()
        if (name.trim().lowercase() !in names) return name
        return generateSequence(2) { it + 1 }
            .map { number ->
                val suffix = " ($number)"
                name.take(MAX_RECIPE_NAME - suffix.length).trimEnd() + suffix
            }
            .first { it.lowercase() !in names }
    }

    private fun comparable(name: String): String = SearchQuery.normalized(name.trim()).replace(SPACES, " ")

    private val SPACES = Regex("""\s+""")
}
