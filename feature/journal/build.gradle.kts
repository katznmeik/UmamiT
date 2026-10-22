plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The journal reads the sessions (JournalRepository) and the saved sheet reads the
    // finished session (SessionRepository); both are in core:data (#36, M1-16).
    implementation(project(":core:data"))
    // The saved sheet is the controller's SavedSheetPort (SCR-06, D-056).
    implementation(project(":core:timer"))
    // Tea cover photos from app-private files (05 §3.9); no network fetcher.
    implementation(libs.coil.compose)
}
