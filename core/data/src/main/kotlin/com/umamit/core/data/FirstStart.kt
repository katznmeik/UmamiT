package com.umamit.core.data

import android.database.SQLException
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The first start in the background, as the setup page (SCR-01) drives it; [FirstStart] on the device. */
interface FirstStartRunner {
    /** The seed photo download's progress for „Teefotos werden geladen …" (ONB-06). */
    val photoProgress: StateFlow<SeedPhotoProgress>

    /** Seeds, then downloads the seed photos, once per process, off the main thread. */
    fun start(): Job

    /** *Überspringen*: the photos keep their placeholder. */
    fun skipPhotos()
}

/**
 * The first start in the background (SCR-01, M1-15): the seed data ([SeedDataSeeder], M1-07),
 * then the one-time seed photo download ([SeedPhotoDownloader], ONB-06), which waits for the seed
 * teas (D-054). It runs off the main thread in its own scope, so it goes on when the setup page
 * is left with *Weiter*; *Überspringen* stops only the download. Both steps are no-ops once done,
 * so the setup started again from *Über* sends nothing.
 */
@Singleton
class FirstStart(
    private val seeder: SeedDataSeeder,
    private val photos: SeedPhotoDownloader,
    private val scope: CoroutineScope,
) : FirstStartRunner {
    @Inject
    constructor(seeder: SeedDataSeeder, photos: SeedPhotoDownloader) :
        this(seeder, photos, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    override val photoProgress: StateFlow<SeedPhotoProgress> get() = photos.progress

    private var job: Job? = null

    /** Starts the first start once per process; later calls return the same job. */
    @Synchronized
    override fun start(): Job = job ?: scope.launch { run() }.also { job = it }

    override fun skipPhotos() = photos.skip()

    private suspend fun run() {
        try {
            seeder.seedIfNeeded()
        } catch (e: IOException) {
            Log.w(TAG, "Seed data not created", e)
        } catch (e: SQLException) {
            Log.w(TAG, "Seed data not created", e)
        }
        // Without seed teas the download sends nothing and stays due (D-054).
        photos.downloadIfNeeded()
    }

    private companion object {
        const val TAG = "UmamiTFirstStart"
    }
}

/** Binds the device's [FirstStartRunner] (M1-15). */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirstStartModule {
    @Binds
    abstract fun firstStart(impl: FirstStart): FirstStartRunner
}
