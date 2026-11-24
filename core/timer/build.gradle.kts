plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

android {
    defaultConfig {
        // The process-death test (#42) runs in the CI emulator job; its runner runs in its
        // own process (src/androidTest/AndroidManifest.xml). It sets the German app language first (GEN-10,
        // src/sharedAndroidTest), so the asserted German texts hold on the en-US emulator.
        testInstrumentationRunner = "com.umamit.core.timer.GermanLocaleRunner"
    }
}

// UnlockedDevice.kt (TMR-A-15, #308) and GermanLocaleRunner.kt (GEN-10): one copy each for the device tests of this
// module and of :app:
// src/sharedAndroidTest is a source folder of both androidTest source sets, not a module. Detekt's
// sources are src/{main,test} and src/androidTest only, so the folder is added to them here.
// The accuracy report and its log parser (AccuracyReport.kt, FireTime.kt; TMR-A-09, D-127) are used
// only by tests: src/sharedTest is a source folder of both the test and the androidTest source sets
// (each sees main's internal declarations), and is added to Detekt's sources the same way.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("androidTest").kotlin.directories.add("$projectDir/src/sharedAndroidTest/kotlin")
    sourceSets.getByName("test").kotlin.directories.add("$projectDir/src/sharedTest/kotlin")
    sourceSets.getByName("androidTest").kotlin.directories.add("$projectDir/src/sharedTest/kotlin")
}
detekt {
    source.from("src/sharedAndroidTest/kotlin", "src/sharedTest/kotlin")
}

dependencies {
    // The BrewSessionController runs the engine and writes the session rows and the
    // active brew through the repositories (04 §4, §7.1); its API uses the engine's
    // events and the session snapshot, so both are part of this module's API.
    api(project(":core:data"))
    // Bundled alert sounds in res/raw (04 §9.2, 06 §3: core:content holds the sounds).
    implementation(project(":core:content"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Robolectric tests for alarm scheduling, notification content and the
    // service (04 §12).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Controller tests (spec #40 seam 2): the real repositories on an in-memory
    // Room database, fake clocks and fake ports from core:testing.
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:database"))

    // Process-death test in the CI emulator job (#42, spec #40 seam 5): the real
    // controller, alarms, receiver, service and database in a test app.
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
