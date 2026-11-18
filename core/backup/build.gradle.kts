plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // The backup's rows, settings and clock (05 §8, BAK-01); in CreateBackup's constructor, so api.
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Robolectric tests against an in-memory Room database, with the real session store and the fake clock.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(project(":core:testing"))
}
