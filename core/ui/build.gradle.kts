plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.android.compose)
}

dependencies {
    // Tea photos from app-private files (06 §4: Coil 3, local files only).
    implementation(libs.coil.compose)
}
