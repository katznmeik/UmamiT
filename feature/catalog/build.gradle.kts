plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The local catalog and the refresh (CatalogRepository, CatalogRefreshRunner; CAT-07, CAT-10).
    implementation(project(":core:data"))
}
