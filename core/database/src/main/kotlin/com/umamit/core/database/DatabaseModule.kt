package com.umamit.core.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // one provider per DAO
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): UmamiDatabase = umamiDatabaseBuilder(context).build()

    @Provides
    fun teaDao(db: UmamiDatabase) = db.teaDao()

    @Provides
    fun recipeDao(db: UmamiDatabase) = db.recipeDao()

    @Provides
    fun vesselDao(db: UmamiDatabase) = db.vesselDao()

    @Provides
    fun inventoryDao(db: UmamiDatabase) = db.inventoryDao()

    @Provides
    fun sessionDao(db: UmamiDatabase) = db.sessionDao()

    /** The tasting reads (05 §3.8): per-tea ratings for the lists, TEA-08. */
    @Provides
    fun tastingDao(db: UmamiDatabase) = db.tastingDao()

    /** The statistics' reads (INS-01–INS-07, D-115). */
    @Provides
    fun statsDao(db: UmamiDatabase) = db.statsDao()

    @Provides
    fun activeBrewDao(db: UmamiDatabase) = db.activeBrewDao()

    /** The Merkliste (05 §3.13, CAT-18): personal data in `umamit.db`, not in the rebuilt catalog. */
    @Provides
    fun catalogBookmarkDao(db: UmamiDatabase) = db.catalogBookmarkDao()

    /** The catalog database `catalog.db` (05 §4): separate from the personal data, never backed up. */
    @Provides
    @Singleton
    fun catalogDatabase(@ApplicationContext context: Context): CatalogDatabase = catalogDatabaseBuilder(context).build()

    @Provides
    fun catalogDao(db: CatalogDatabase) = db.catalogDao()
}
