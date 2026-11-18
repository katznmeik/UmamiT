plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    api(project(":core:model"))
    // The recipe file (05 §8a): CAT-17 AC2's shop link check (TeaLinks) and the built-in styles.
    implementation(project(":core:catalog-parser"))
    implementation(libs.kotlinx.serialization.json)

    // The share text's golden against the module's own strings.xml (PRF-08 AC1).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
