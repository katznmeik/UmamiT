plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The Schritte-Sheet (SCR-31) emits the engine's EditSteps request and uses its
    // TMR-10 default for *Aufguss hinzufügen* (D-048).
    implementation(project(":core:timer-engine"))
    // BackHandler: back with unsaved edits asks „Änderungen verwerfen?" (GEN-08).
    implementation(libs.androidx.activity.compose)

    // Integration test (#38): the save request through the Room recipe repository.
    testImplementation(project(":core:database"))
    testImplementation(libs.androidx.test.core)
}

// Record-only motion frames (StepsSheetMotionRecordingTest): they run only with
// `-Pumamit.recordMotion=true`, never in CI or `verifyRoborazziDebug`.
// Use `scripts/motion/record-gifs.sh`.
val recordMotion = providers.gradleProperty("umamit.recordMotion").map { it.toBoolean() }.orElse(false)
val motionOutputDir = layout.buildDirectory.dir("outputs/motion").map { it.asFile.absolutePath }
tasks.withType<Test>().configureEach {
    if (recordMotion.get()) {
        systemProperty("umamit.motionOutputDir", motionOutputDir.get())
        systemProperty("roborazzi.test.record", "true")
        outputs.upToDateWhen { false }
    }
}
