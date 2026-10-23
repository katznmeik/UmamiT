package com.umamit.core.testing

import com.umamit.core.data.FirstStartRunner
import com.umamit.core.data.SeedPhotoProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [FirstStartRunner]: counts starts and skips; tests set the [photoProgress]. */
class FakeFirstStartRunner(initial: SeedPhotoProgress = SeedPhotoProgress.Idle) : FirstStartRunner {
    override val photoProgress = MutableStateFlow(initial)

    var starts = 0
        private set

    var skips = 0
        private set

    override fun start(): Job {
        starts++
        return Job().apply { complete() }
    }

    override fun skipPhotos() {
        skips++
        photoProgress.value = SeedPhotoProgress.Skipped
    }
}
