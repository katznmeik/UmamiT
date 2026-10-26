package com.umamit.core.catalog.parser

/**
 * The refresh's structural sanity check (08 §2.9, CAT-07 AC5, D-076): a refresh that fails it
 * changes nothing, because the shop's pages most likely changed under the parser.
 *
 * @param firstImportMinimum the green teas a first import needs (150); a simulated shop in a
 *   test lowers it.
 */
class SanityCheck(private val firstImportMinimum: Int = FIRST_IMPORT_MINIMUM) {
    /**
     * What one refresh parsed.
     *
     * @property previousGreen the green teas of the catalog before the refresh; 0 for a first import.
     * @property green the green teas parsed now.
     * @property defective classification pages that gave no product at all or no breadcrumb.
     * @property lackingRequired green teas without name, price or article number.
     * @property lackingBrewing green teas without brewing grid and prose; null while the parser
     *   doesn't read them, then that threshold is not judged.
     */
    data class Counts(
        val previousGreen: Int,
        val green: Int,
        val defective: Int,
        val lackingRequired: Int,
        val lackingBrewing: Int?,
    )

    /** A failed threshold. */
    enum class Problem {
        /** Fewer than half the previous green teas, or fewer than the first-import minimum. */
        TOO_FEW_PRODUCTS,

        /** More than 30 % of the products lack name, price or article number. */
        REQUIRED_FIELDS_MISSING,

        /** More than half of the green teas lack a brewing grid and brewing prose. */
        BREWING_INFO_MISSING,
    }

    /** The thresholds [counts] fails, in [Problem] order; empty when the refresh may be swapped in. */
    fun problems(counts: Counts): List<Problem> = buildList {
        // Integer arithmetic, so a count exactly on a threshold passes (08 §2.9 says "fewer", "more than").
        val tooFew = if (counts.previousGreen == 0) {
            counts.green < firstImportMinimum
        } else {
            counts.green * 2 < counts.previousGreen
        }
        if (tooFew) add(Problem.TOO_FEW_PRODUCTS)
        val products = counts.green + counts.defective
        val lacking = counts.lackingRequired + counts.defective
        if (lacking * PERCENT > products * MAX_LACKING_REQUIRED_PERCENT) add(Problem.REQUIRED_FIELDS_MISSING)
        val lackingBrewing = counts.lackingBrewing
        if (lackingBrewing != null && lackingBrewing * 2 > counts.green) add(Problem.BREWING_INFO_MISSING)
    }

    companion object {
        /** 08 §2.9: a first import with fewer green teas is a broken parser, not a small shop. */
        const val FIRST_IMPORT_MINIMUM = 150
        private const val PERCENT = 100
        private const val MAX_LACKING_REQUIRED_PERCENT = 30
    }
}
