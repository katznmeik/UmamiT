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

    @Provides
    fun activeBrewDao(db: UmamiDatabase) = db.activeBrewDao()
}
