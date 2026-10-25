plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.android.compose)
}

dependencies {
    // KettleWater (BRW-10), shared with the notifications of core:timer (D-068).
    api(project(":core:model"))
    testImplementation(libs.androidx.test.core)
}

// Record-only motion GIFs (MotionRecordingTest): they run only with
// `-Pumamit.recordMotion=true`, never in CI or `verifyRoborazziDebug`.
// Use `scripts/motion/record-gifs.sh`.
val recordMotion = providers.gradleProperty("umamit.recordMotion").map { it.toBoolean() }.orElse(false)
val motionOutputDir = layout.buildDirectory.dir("outputs/motion").map { it.asFile.absolutePath }
tasks.withType<Test>().configureEach {
    if (recordMotion.get()) {
        systemProperty("umamit.motionOutputDir", motionOutputDir.get())
        // recordRoboVideo writes files only in Roborazzi's record mode.
        systemProperty("roborazzi.test.record", "true")
        outputs.upToDateWhen { false }
    }
}
