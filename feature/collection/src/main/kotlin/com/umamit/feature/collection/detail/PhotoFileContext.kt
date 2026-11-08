package com.umamit.feature.collection.detail

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import kotlin.coroutines.CoroutineContext

/** Where the tea detail checks that its photo files exist: off the main thread; a test leaves it empty. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhotoFileContext

/** Provides [PhotoFileContext] as the IO dispatcher. */
@Module
@InstallIn(SingletonComponent::class)
object PhotoFileContextModule {
    @Provides
    @PhotoFileContext
    fun photoFileContext(): CoroutineContext = Dispatchers.IO
}
