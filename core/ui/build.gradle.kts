plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.android.compose)
}

dependencies {
    // „Rezept prüfen" with its reason (RecipeCheckNote; CAT-05): the model's reason and the badge.
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    // The refresh's progress and result lines (SCR-22, SCR-25; CAT-07, SET-23): its state and outcome types and
    // the data_catalog_* words it shares with the refresh notifications.
    implementation(project(":core:data"))
    // Tea photos from app-private files (06 §4: Coil 3, local files only).
    implementation(libs.coil.compose)
    // The POST_NOTIFICATIONS request of the reliability rows (ONB-02, SET-24).
    implementation(libs.androidx.activity.compose)
    // InfusionTasteEditor saves a pending note when the app stops (JRN-05).
    implementation(libs.androidx.lifecycle.runtime.compose)
    // `toUri` for openInBrowser (D-123).
    implementation(libs.androidx.core.ktx)
}
