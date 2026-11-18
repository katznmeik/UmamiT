package com.umamit.core.backup.archive

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

/**
 * The backup writer's bindings of its own (05 §8). [CreateBackup] also needs `UmamiDatabase`, `SettingsDataSource`
 * and `WallClock`, which `core:database`, `core:datastore` and `core:data` bind.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    /** `filesDir/photos/`, the folder `core:data`'s `PhotoFiles` writes (05 §3.9). */
    @Provides
    fun photoDirectory(@ApplicationContext context: Context) = BackupPhotoDirectory(File(context.filesDir, "photos"))

    @Provides
    fun appVersion(@ApplicationContext context: Context): AppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppVersion(name = info.versionName.orEmpty(), code = info.longVersionCode)
    }
}
