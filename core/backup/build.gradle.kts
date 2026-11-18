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
    api(project(":core:model"))
    // The recipe file (05 §8a): CAT-17 AC2's shop link check (TeaLinks) and the built-in styles.
    implementation(project(":core:catalog-parser"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Robolectric tests against an in-memory Room database, with the real session store and the fake clock;
    // the share text's golden against the module's own strings.xml (PRF-08 AC1).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(project(":core:testing"))
}
