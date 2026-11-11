package com.umamit.feature.journal.session

import com.umamit.core.domain.search.SearchQuery
import com.umamit.core.model.DescriptorGroup
import com.umamit.core.model.TasteDescriptor

/**
 * The descriptor sheet's search (SCR-30, GEN-10): the groups, in the vocabulary's order, with the descriptors whose
 * [label] a word of [query] starts, ignoring case and accents. A query without words keeps everything; a group
 * without a match is left out.
 */
internal fun matchingGroups(
    query: String,
    label: (TasteDescriptor) -> String,
): List<Pair<DescriptorGroup, List<TasteDescriptor>>> {
    val search = SearchQuery(query)
    val hits = TasteDescriptor.entries.filter { search.isEmpty || search.matches(SearchQuery.words(label(it))) }
    return DescriptorGroup.entries.map { group -> group to hits.filter { it.group == group } }
        .filter { (_, members) -> members.isNotEmpty() }
}
