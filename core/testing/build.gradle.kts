plugins {
    alias(libs.plugins.umamit.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    // Fakes of the BrewSessionController's ports and clocks (spec #40 seam 2).
    api(project(":core:timer"))
    api(libs.kotlinx.coroutines.core)
}
