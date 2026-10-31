package com.umamit.core.data.catalog

import android.util.Log
import com.umamit.core.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * The folded *Sorte* groups of the catalog list (v0.3.0, CAT-10, SCR-19, D-098), as the keys of [CatalogFoldKey].
 * Remembered across app starts in the setting `catalogFolded`.
 */
interface CatalogFoldRepository {
    /** The keys of the folded groups, both views. */
    fun observeFolded(): Flow<Set<String>>

    /** Replaces the folded keys by [transform] of them, atomically. A failed write is logged and changes nothing. */
    suspend fun update(transform: (Set<String>) -> Set<String>)

    companion object {
        /** No folding: nothing is folded and changes are dropped (the default where none is wired). */
        val NONE: CatalogFoldRepository = object : CatalogFoldRepository {
            override fun observeFolded(): Flow<Set<String>> = flowOf(emptySet())

            override suspend fun update(transform: (Set<String>) -> Set<String>) = Unit
        }
    }
}

/** The key of a folded group in `catalogFolded` (05 §6, D-098): `<view>/<sorte key>`, „Ohne Sorte" as `<view>/-`. */
object CatalogFoldKey {
    private const val GREEN = "green"
    private const val HERBAL = "herbal"
    private const val UNTYPED = "-"

    /** The group of [teaType] (null = „Ohne Sorte") in the Kräutertee view when [herbal], else the green one. */
    fun of(herbal: Boolean, teaType: String?): String = "${prefix(herbal)}${teaType ?: UNTYPED}"

    /** Every key of the Kräutertee view when [herbal], else of the green one, starts with this. */
    fun prefix(herbal: Boolean): String = "${if (herbal) HERBAL else GREEN}/"
}

/** [CatalogFoldRepository] on the setting `catalogFolded`. */
class SettingsCatalogFoldRepository @Inject constructor(private val settings: SettingsRepository) :
    CatalogFoldRepository {
    override fun observeFolded(): Flow<Set<String>> = settings.settings.map { it.catalogFolded }.distinctUntilChanged()

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        try {
            settings.update { it.copy(catalogFolded = transform(it.catalogFolded)) }
        } catch (e: IOException) {
            // Folding is a convenience: a full disk keeps the groups as they were, never a crash.
            Log.w(TAG, "Folded catalog groups not saved", e)
        }
    }

    private companion object {
        const val TAG = "UmamiTCatalogFolds"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogFoldModule {
    @Binds
    abstract fun catalogFoldRepository(impl: SettingsCatalogFoldRepository): CatalogFoldRepository
}
