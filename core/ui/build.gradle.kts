plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.android.compose)
}

dependencies {
    // „Rezept prüfen" with its reason (RecipeCheckNote; CAT-05): the model's reason and the badge.
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    // Tea photos from app-private files (06 §4: Coil 3, local files only).
    implementation(libs.coil.compose)
    // The POST_NOTIFICATIONS request of the reliability rows (ONB-02, SET-24).
    implementation(libs.androidx.activity.compose)
}
