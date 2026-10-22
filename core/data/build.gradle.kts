plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    api(project(":core:model"))
    // WallClock (repository constructors) and snap5 (GEN-12).
    api(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.core)

    // Robolectric tests against an in-memory Room database (05, #30).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.datastore)
    // FakeSettingsRepository for the seeder tests (M1-07).
    testImplementation(project(":core:testing"))
}
