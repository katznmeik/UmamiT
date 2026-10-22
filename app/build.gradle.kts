plugins {
    alias(libs.plugins.umamit.android.application)
    alias(libs.plugins.umamit.android.compose)
    alias(libs.plugins.umamit.hilt)
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

    debugImplementation(libs.leakcanary.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// The build-logic unit tests (versionCode, 06 §6) run with the normal unit tests.
tasks.named { it == "testDebugUnitTest" }.configureEach {
    dependsOn(":buildLogicTest")
}
