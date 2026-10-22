plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // Start (SCR-02) reads the collection and the recent sessions, shows the active session
    // and starts *Nochmal brühen* through the controller's start seam (BrewStarter, BRW-11).
    implementation(project(":core:timer"))
}
