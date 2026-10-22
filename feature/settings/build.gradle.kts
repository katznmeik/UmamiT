plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // *Diagnose* reads the app's diagnostics log (06 §5.6, M1-20).
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.test.core)
}
