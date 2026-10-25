plugins {
    alias(libs.plugins.umamit.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    // The catalog list's real filters behind FakeCatalogRepository (CAT-10).
    implementation(project(":core:domain"))
    // Fakes of the BrewSessionController's ports and clocks (spec #40 seam 2).
    api(project(":core:timer"))
    api(libs.kotlinx.coroutines.core)
}
