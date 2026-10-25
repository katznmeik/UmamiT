plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // *Diagnose* reads the app's diagnostics log (06 §5.6, M1-20).
    implementation(project(":core:data"))
    // *Alarm testen* (SET-31, AlertTester), the tone names (AlertSound) and the reliability
    // checks of the *Timer-Zuverlässigkeit* card (SET-24).
    implementation(project(":core:timer"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.test.core)
}
