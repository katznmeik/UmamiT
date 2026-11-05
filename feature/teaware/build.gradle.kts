plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The vessels (VesselRepository) and *Archivieren* / *Wiederherstellen* (VesselArchive; WARE-01, D-100).
    implementation(project(":core:data"))
    // BackHandler: back with unsaved edits asks „Änderungen verwerfen?" (GEN-08).
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.test.core)
}
