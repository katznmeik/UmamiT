plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The rows of *Meine Tees*, the settings (collectionSort) and *Wiederherstellen* (TeaArchive; SCR-08, D-100).
    implementation(project(":core:data"))
    // Search, filters and sort as pure functions (CollectionBrowser; TEA-09–TEA-11).
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
