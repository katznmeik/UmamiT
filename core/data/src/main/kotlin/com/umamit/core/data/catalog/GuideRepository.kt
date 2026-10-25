package com.umamit.core.data.catalog

import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogGuideEntity
import com.umamit.core.domain.guide.GuideMarkdown
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDetail
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideSummary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** The *Ratgeber* (CAT-09, SCR-20): the guides of the last successful refresh, read only and offline. */
interface GuideRepository {
    /** Every guide by title. */
    fun observeGuides(): Flow<List<GuideSummary>>

    /** One guide with its article, or null when the catalog has none with [slug]. */
    fun observeGuide(slug: String): Flow<GuideDetail?>

    companion object {
        /** No guides at all, for screens and tests that don't show them. */
        val NONE: GuideRepository = object : GuideRepository {
            override fun observeGuides(): Flow<List<GuideSummary>> = flowOf(emptyList())

            override fun observeGuide(slug: String): Flow<GuideDetail?> = flowOf(null)
        }
    }
}

/** [GuideRepository] on `catalog.db` and the guide images in `filesDir/catalog/guides/` (D-087). */
class RoomGuideRepository @Inject constructor(private val dao: CatalogDao, private val photos: CatalogPhotoFiles) :
    GuideRepository {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    override fun observeGuides(): Flow<List<GuideSummary>> = dao.observeGuides().map { guides ->
        guides.map { GuideSummary(it.slug, it.title, it.publishedAt, imagePath(it.heroFile)) }
            .sortedWith { a, b -> collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.slug.compareTo(b.slug) }
    }.distinctUntilChanged()

    override fun observeGuide(slug: String): Flow<GuideDetail?> =
        dao.observeGuide(slug).map { it?.toDetail() }.distinctUntilChanged()

    private fun CatalogGuideEntity.toDetail() = GuideDetail(
        slug = slug,
        title = title,
        url = url,
        publishedAtMillis = publishedAt,
        heroPath = imagePath(heroFile),
        body = GuideDocument(
            GuideMarkdown.read(bodyMarkdown).blocks.mapNotNull { block ->
                if (block is GuideBlock.Image) imagePath(block.source)?.let { block.copy(source = it) } else block
            },
        ),
    )

    /** A stored image name as its file's path; anything else (never written by the refresh) as none. */
    private fun imagePath(name: String?): String? =
        name?.takeIf { GuideImageFiles.isName(it) }?.let { photos.guideImages.file(it).path }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GuideModule {
    @Binds
    abstract fun guideRepository(impl: RoomGuideRepository): GuideRepository
}
