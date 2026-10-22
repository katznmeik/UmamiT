package com.umamit.core.data

import android.content.Context
import com.umamit.core.common.SystemWallClock
import com.umamit.core.common.WallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.PHOTOS_DIR
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

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
        fun photoFiles(@ApplicationContext context: Context): PhotoFiles =
            PhotoFiles(File(context.filesDir, PHOTOS_DIR))

        /**
         * The diagnostics log (06 §5.6): in the no-backup storage, which no backup or
         * device transfer includes (05 §8), written on the IO dispatcher.
         */
        @Provides
        @Singleton
        fun diagnosticsLog(@ApplicationContext context: Context): DiagnosticsLog = DiagnosticsLog(
            directory = File(context.noBackupFilesDir, DiagnosticsLog.DIRECTORY),
            maxBytes = DiagnosticsLog.MAX_BYTES,
            dispatcher = Dispatchers.IO,
        )
    }
}
