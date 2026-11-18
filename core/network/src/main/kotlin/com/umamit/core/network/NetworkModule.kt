package com.umamit.core.network

import android.content.Context
import android.content.pm.PackageManager
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
     * its single connection and its rate limit. Its User-Agent carries the app's versionName, or „unknown" when
     * the package cannot be looked up: never on a device, but in a Robolectric test whose app-start launch builds
     * the graph after that test's package manager was reset; the throw then escaped into the next test.
     */
    @Provides
    @Singleton
    fun shopClient(@ApplicationContext context: Context): ShopClient {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return ShopClient.create(appVersion = version ?: "unknown")
    }
}
