package com.umamit.core.testing

import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [CatalogRepository]: tests set [catalog]. */
class FakeCatalogRepository(initial: CatalogOverview = CatalogOverview(emptyList(), null)) : CatalogRepository {
    val catalog = MutableStateFlow(initial)

    override fun observeCatalog(): Flow<CatalogOverview> = catalog
}

/** In-memory [CatalogRefreshRunner]: counts the calls; tests drive [state]. */
class FakeCatalogRefreshRunner(initial: CatalogRefreshState = CatalogRefreshState.Idle) : CatalogRefreshRunner {
    override val state = MutableStateFlow(initial)

    var starts = 0
        private set

    var cancels = 0
        private set

    var acknowledgements = 0
        private set

    override fun start() {
        starts++
    }

    override fun cancel() {
        cancels++
    }

    override fun acknowledge() {
        acknowledgements++
        if (state.value is CatalogRefreshState.Finished) state.value = CatalogRefreshState.Idle
    }
}

/** A catalog tea for tests: invented names (D-016). */
fun catalogTea(key: String, name: String, teaType: String? = "Sencha", basePriceCentsPer100g: Long? = 1240) =
    CatalogTeaSummary(
        key = key,
        name = name,
        secondaryName = null,
        teaType = teaType,
        basePriceCentsPer100g = basePriceCentsPer100g,
        available = true,
    )
