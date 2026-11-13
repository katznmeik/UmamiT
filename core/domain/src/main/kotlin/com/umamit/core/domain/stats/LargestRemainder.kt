package com.umamit.core.domain.stats

private const val WHOLE = 100

/**
 * Whole percentages of [counts] that sum to 100 (largest remainder): each gets its floor, the points left go to
 * the largest remainders; a tie goes to the larger count, then to the earlier entry. All 0 when the total is 0.
 */
internal fun wholePercentages(counts: List<Int>): List<Int> {
    val total = counts.sum()
    if (total == 0) return counts.map { 0 }
    val floors = counts.map { it * WHOLE / total }.toMutableList()
    val left = WHOLE - floors.sum()
    counts.indices
        .sortedWith(compareByDescending<Int> { counts[it] * WHOLE % total }.thenByDescending { counts[it] })
        .take(left)
        .forEach { floors[it]++ }
    return floors
}
