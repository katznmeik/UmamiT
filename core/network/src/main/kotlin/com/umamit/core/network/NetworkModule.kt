package com.umamit.core.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * The one [ShopClient] of the app: a singleton, so all four network uses (GEN-01) share
     * its single connection and its rate limit. Its User-Agent carries the app's versionName.
     */
    @Provides
    @Singleton
    fun shopClient(@ApplicationContext context: Context): ShopClient {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        return ShopClient.create(appVersion = version)
    }
}
