plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    api(project(":core:model"))
    // WallClock (repository constructors) and snap5 (GEN-12).
    api(project(":core:common"))
    // The session repositories and the active brew store take the timer engine's
    // planned steps, step records and snapshot (04 §4, §7.1; D-052).
    api(project(":core:timer-engine"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    // The allow-listed shop client for the seed photo download (ONB-06) and the catalog refresh (CAT-07).
    implementation(project(":core:network"))
    // Sitemap reader, product page parser and sanity check of the catalog refresh (06 §3, 08 §2).
    implementation(project(":core:catalog-parser"))
    implementation(libs.kotlinx.coroutines.core)
    // Bitmap.scale for re-encoding the seed photos (05 §3.9, D-050).
    implementation(libs.androidx.core.ktx)

    // Robolectric tests against an in-memory Room database (05, #30).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.datastore)
    // FakeSettingsRepository for the seeder tests (M1-07).
    testImplementation(project(":core:testing"))
    // The synthetic shop for the seed photo download tests (#37) and the catalog refresh tests (#64).
    testImplementation(libs.okhttp.mockwebserver3)
    // Synthetic product pages and sitemaps (D-016).
    testImplementation(testFixtures(project(":core:catalog-parser")))
}
