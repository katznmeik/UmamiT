package com.umamit.core.data

import android.database.SQLException
import android.util.Log
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
 *
 * A seed that failed is retried at every later app start ([retrySeedAtAppStart], D-075). The
 * downloader (and its shop client) is created only when the setup needs it, never for the retry.
 */
@Singleton
class FirstStart(
    private val seeder: SeedDataSeeder,
    private val photoDownloader: Lazy<SeedPhotoDownloader>,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : FirstStartRunner {
    @Inject
    constructor(seeder: SeedDataSeeder, photos: Lazy<SeedPhotoDownloader>, settings: SettingsRepository) :
        this(seeder, photos, settings, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val photos: SeedPhotoDownloader get() = photoDownloader.get()

    override val photoProgress: StateFlow<SeedPhotoProgress> get() = photos.progress

    private var job: Job? = null

    private var retryJob: Job? = null

    /** Starts the first start once per process; later calls return the same job. */
    @Synchronized
    override fun start(): Job = job ?: scope.launch { run() }.also { job = it }

    override fun skipPhotos() = photos.skip()

    /**
     * The app start's seed retry (D-075, #58 D-O1): once the setup is done, seeds while the seed
     * marker is still false, so Start is never left without the seed teas after a failed first
     * start. Only the seed: the one-time photo download is never sent from here (CAT-15 fills
     * the covers). Before the setup is done the setup page's [start] seeds. Once per process,
     * off the main thread; a failure is logged and retried at the next start.
     */
    @Synchronized
    fun retrySeedAtAppStart(): Job = retryJob ?: scope.launch {
        val setupDone = try {
            settings.settings.first().onboardingCompleted
        } catch (e: IOException) {
            Log.w(TAG, "Settings not read", e)
            false
        }
        if (setupDone) seed()
    }.also { retryJob = it }

    private suspend fun run() {
        seed()
        // Without seed teas the download sends nothing and stays due (D-054).
        photos.downloadIfNeeded()
    }

    private suspend fun seed() {
        try {
            seeder.seedIfNeeded()
        } catch (e: IOException) {
            Log.w(TAG, "Seed data not created", e)
        } catch (e: SQLException) {
            Log.w(TAG, "Seed data not created", e)
        }
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
