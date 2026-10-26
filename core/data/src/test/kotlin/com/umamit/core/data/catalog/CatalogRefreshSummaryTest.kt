package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogRefreshOutcome.Success
import com.umamit.core.data.catalog.SummaryPart.Changed
import com.umamit.core.data.catalog.SummaryPart.Discontinued
import com.umamit.core.data.catalog.SummaryPart.New
import com.umamit.core.data.catalog.SummaryPart.NoChanges
import com.umamit.core.data.catalog.SummaryPart.RecipesToCheck
import com.umamit.core.data.catalog.SummaryPart.Teas
import org.junit.Test

/** The refresh's summary line (CAT-07 AC3, SCR-22, #70): which parts it shows, in which order. */
class CatalogRefreshSummaryTest {
    @Test
    fun aLaterRefreshNamesEachKindOfChangeInTheSpecsOrder() {
        val success = Success(teas = 276, newTeas = 12, changedTeas = 5, discontinuedTeas = 2, recipesToCheck = 3)

        assertThat(success.summary())
            .containsExactly(Teas(276), New(12), Changed(5), Discontinued(2), RecipesToCheck(3))
            .inOrder()
    }

    @Test
    fun aKindWithoutChangesIsLeftOut() {
        assertThat(Success(teas = 274, newTeas = 0, discontinuedTeas = 1).summary())
            .containsExactly(Teas(274), Discontinued(1))
            .inOrder()
    }

    @Test
    fun aLaterRefreshWithoutAnyChangeSaysSo() {
        assertThat(Success(teas = 274, newTeas = 0).summary()).containsExactly(Teas(274), NoChanges).inOrder()
    }

    @Test
    fun aFirstLoadDoesNotCallEveryTeaNew() {
        // Every tea is new on the first load: „274 neu" would say nothing (like the „Neu" badge, D-083).
        assertThat(Success(teas = 274, newTeas = 274, recipesToCheck = 31).summary())
            .containsExactly(Teas(274), RecipesToCheck(31))
            .inOrder()
        assertThat(Success(teas = 274, newTeas = 274).summary()).containsExactly(Teas(274))
    }
}
