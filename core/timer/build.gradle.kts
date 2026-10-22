plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Robolectric tests for alarm scheduling, notification content and the
    // service (04 §12).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
