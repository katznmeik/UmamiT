package com.umamit.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** File `files/datastore/settings.json` (included in the device backup, 05 §8). */
    const val SETTINGS_FILE = "settings.json"

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<StoredSettings> =
        DataStoreFactory.create(SettingsSerializer) { context.dataStoreFile(SETTINGS_FILE) }
}
