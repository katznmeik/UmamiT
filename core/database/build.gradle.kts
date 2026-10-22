plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.android.room)
    alias(libs.plugins.umamit.hilt)
}

// The exported schemas (05 §9) are read by MigrationTestHelper in the Robolectric tests.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
