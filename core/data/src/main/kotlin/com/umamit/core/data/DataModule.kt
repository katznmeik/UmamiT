package com.umamit.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun teaRepository(impl: RoomTeaRepository): TeaRepository

    @Binds
    abstract fun recipeRepository(impl: RoomRecipeRepository): RecipeRepository

    @Binds
    abstract fun vesselRepository(impl: RoomVesselRepository): VesselRepository

    @Binds
    abstract fun stockRepository(impl: RoomStockRepository): StockRepository

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        fun timeSource(): TimeSource = TimeSource(System::currentTimeMillis)
    }
}
