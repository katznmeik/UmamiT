plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The figures (ObserveStats, SettingsRepository's statsRange and statsTab; INS-07) and their types (#256).
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    // Tea cover photos from app-private files in the *Am häufigsten* rows (INS-01); no network fetcher.
    implementation(libs.coil.compose)
}
