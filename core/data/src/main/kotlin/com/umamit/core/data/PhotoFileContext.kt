package com.umamit.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import kotlin.coroutines.CoroutineContext

/**
 * Where a screen checks that its photo files exist (the tea detail, the session page's hero): off the main thread; a
 * test leaves it empty.
 */
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
