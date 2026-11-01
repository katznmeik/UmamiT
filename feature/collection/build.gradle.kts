plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The tea detail (SCR-09): teas, recipes, vessels, stock, the journal, the catalog's reorder link (INV-09)
    // and TeaArchive (TEA-04).
    implementation(project(":core:data"))
}
