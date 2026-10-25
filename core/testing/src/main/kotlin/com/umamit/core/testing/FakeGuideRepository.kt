package com.umamit.core.testing

import com.umamit.core.data.catalog.GuideRepository
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [GuideRepository]: tests set the [guides]; the list is their summaries by title. */
class FakeGuideRepository(guides: List<GuideDetail> = emptyList()) : GuideRepository {
    val guides = MutableStateFlow(guides)

    override fun observeGuides(): Flow<List<GuideSummary>> = guides.map { all ->
        all.sortedBy { it.title }.map { GuideSummary(it.slug, it.title, it.publishedAtMillis, it.heroPath) }
    }

    override fun observeGuide(slug: String): Flow<GuideDetail?> = guides.map { all ->
        all.firstOrNull { it.slug == slug }
    }
}
