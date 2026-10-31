plugins {
    alias(libs.plugins.umamit.android.application)
    alias(libs.plugins.umamit.android.compose)
    alias(libs.plugins.umamit.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.umamit.app"
    defaultConfig {
        applicationId = "com.umamit.app" // D-027: never change after the first release
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }
    // The app is German only; drop other locales from library resources.
    androidResources {
        localeFilters += "de"
    }
}

dependencies {
    // DI root: the app wires every feature and the core modules it hosts.
    listOf("onboarding", "home", "brew", "collection", "catalog", "journal", "stats", "teaware", "settings")
        .forEach { implementation(project(":feature:$it")) }
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:timer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    // The Start tab's view models, which tests replace with ones on fakes (#72).
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // WorkManager on demand with Hilt's worker factory, for the daily low-stock reminder (INV-11, 06 §5.4, D-098).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.truth)
    // Fake repositories and controller for the journal, the saved sheet (M1-16) and the Start and setup flows (M1-13).
    testImplementation(project(":core:testing"))
    // Start on the real controller with an in-memory database (#47, BRW-11).
    testImplementation(project(":core:database"))
    // The real settings file, for the start destination with an unreadable file (D-066, D-068).
    testImplementation(project(":core:datastore"))

    debugImplementation(libs.leakcanary.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// The build-logic unit tests (versionCode, 06 §6) run with the normal unit tests.
tasks.named { it == "testDebugUnitTest" }.configureEach {
    dependsOn(":buildLogicTest")
}
