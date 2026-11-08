package com.umamit.core.testing

import com.umamit.core.data.catalog.CatalogReset
import com.umamit.core.data.catalog.CatalogResetPlan
import com.umamit.core.data.catalog.CatalogResetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [CatalogReset] by hand: [plans] holds the reset each tea is offered (by tea id; none: not offered), [resets]
 * records every reset, and [failNext] makes the next one fail. A reset of a tea without a plan finds no catalog
 * tea.
 */
class FakeCatalogReset(plans: Map<String, CatalogResetPlan> = emptyMap()) : CatalogReset {
    val plans = MutableStateFlow(plans)

    val resets = mutableListOf<String>()

    var failNext = false

    override fun observePlan(teaId: String): Flow<CatalogResetPlan?> = plans.map { it[teaId] }.distinctUntilChanged()

    override suspend fun reset(teaId: String): CatalogResetResult {
        val plan = plans.value[teaId]
        return when {
            plan == null -> CatalogResetResult.NotInCatalog

            failNext -> {
                failNext = false
                CatalogResetResult.Failed
            }

            else -> {
                resets += teaId
                CatalogResetResult.Reset(plan.leftOut)
            }
        }
    }
}
