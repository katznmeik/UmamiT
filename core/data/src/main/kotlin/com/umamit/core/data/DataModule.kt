package com.umamit.core.data

import android.content.Context
import com.umamit.core.common.SystemWallClock
import com.umamit.core.common.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

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

    @Binds
    abstract fun sessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    abstract fun activeBrewStore(impl: RoomActiveBrewStore): ActiveBrewStore

    @Binds
    abstract fun databaseTransactions(impl: RoomDatabaseTransactions): DatabaseTransactions

    companion object {
        /** Wall clock for `created_at` / `updated_at` (epoch ms UTC, 05 §1.4). */
        @Provides
        fun wallClock(): WallClock = SystemWallClock

        /** Photo files in app-private `filesDir/photos/` (05 §3.9, 06 §5.2). */
        @Provides
        fun photoFiles(@ApplicationContext context: Context): PhotoFiles = PhotoFiles(File(context.filesDir, "photos"))
    }
}
