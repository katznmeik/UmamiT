package com.umamit.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** The dispatcher the statistics' figures are computed on (INS-01–INS-07): off the main thread; a test swaps it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StatsDispatcher

/** Provides [StatsDispatcher] as [Dispatchers.Default]. */
@Module
@InstallIn(SingletonComponent::class)
object StatsDispatcherModule {
    @Provides
    @StatsDispatcher
    fun statsDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
