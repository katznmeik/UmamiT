plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    // The shop client (06 §4, GEN-01, CLAUDE.md rule 7). `api`: callers build HttpUrls.
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver3)
    // NetworkModuleTest: the User-Agent's version against a real (and an unknown) package.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
