plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // The reliability checks (TMR-31) and the first start's seeding and seed photos (ONB-06).
    implementation(project(":core:timer"))
    // BackHandler: back on the reliability page returns to the welcome.
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.androidx.test.core)
}
