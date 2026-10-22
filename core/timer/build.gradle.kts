plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
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
}
