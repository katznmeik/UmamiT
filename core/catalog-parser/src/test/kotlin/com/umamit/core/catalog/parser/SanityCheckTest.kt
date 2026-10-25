package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.umamit.core.catalog.parser.SanityCheck.Problem
import org.junit.Test

/** The refresh's sanity thresholds (08 §2.9, CAT-07 AC5, D-076). */
class SanityCheckTest {
    private val check = SanityCheck()

    private fun problems(
        previous: Int = 0,
        green: Int,
        defective: Int = 0,
        lackingRequired: Int = 0,
        lackingBrewing: Int? = null,
    ) = check.problems(
        SanityCheck.Counts(
            previousGreen = previous,
            green = green,
            defective = defective,
            lackingRequired = lackingRequired,
            lackingBrewing = lackingBrewing,
        ),
    )

    @Test
    fun aFirstImportNeedsAtLeast150GreenTeas() {
        assertThat(problems(green = 150)).isEmpty()
        assertThat(problems(green = 149)).containsExactly(Problem.TOO_FEW_PRODUCTS)
        assertThat(problems(green = 0)).containsExactly(Problem.TOO_FEW_PRODUCTS)
    }

    @Test
    fun aLaterRefreshNeedsHalfThePreviousCount() {
        assertThat(problems(previous = 280, green = 140)).isEmpty()
        assertThat(problems(previous = 280, green = 139)).containsExactly(Problem.TOO_FEW_PRODUCTS)
        // The first-import floor applies only to the first import.
        assertThat(problems(previous = 100, green = 60)).isEmpty()
    }

    @Test
    fun moreThan30PercentWithoutNamePriceOrNumberIsAStructuralFailure() {
        assertThat(problems(green = 200, lackingRequired = 60)).isEmpty()
        assertThat(problems(green = 200, lackingRequired = 61)).containsExactly(Problem.REQUIRED_FIELDS_MISSING)
        // Pages that gave nothing at all count among the products and as lacking.
        assertThat(problems(green = 200, defective = 100)).containsExactly(Problem.REQUIRED_FIELDS_MISSING)
        assertThat(problems(green = 200, defective = 30, lackingRequired = 39)).isEmpty()
        assertThat(
            problems(green = 200, defective = 30, lackingRequired = 40),
        ).containsExactly(Problem.REQUIRED_FIELDS_MISSING)
    }

    @Test
    fun moreThanHalfWithoutBrewingGridAndProseIsAStructuralFailure() {
        assertThat(problems(green = 200, lackingBrewing = 100)).isEmpty()
        assertThat(problems(green = 200, lackingBrewing = 101)).containsExactly(Problem.BREWING_INFO_MISSING)
        // Not judged while the parser doesn't read the brewing information.
        assertThat(problems(green = 200, lackingBrewing = null)).isEmpty()
    }

    @Test
    fun everyFailedThresholdIsReported() {
        assertThat(problems(previous = 300, green = 100, lackingRequired = 100, lackingBrewing = 100))
            .containsExactly(Problem.TOO_FEW_PRODUCTS, Problem.REQUIRED_FIELDS_MISSING, Problem.BREWING_INFO_MISSING)
    }

    @Test
    fun theFirstImportFloorCanBeLoweredForASimulatedShop() {
        assertThat(SanityCheck(firstImportMinimum = 2).problems(SanityCheck.Counts(0, 2, 0, 0, null))).isEmpty()
    }
}
