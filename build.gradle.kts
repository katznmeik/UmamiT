// Root build: plugins are declared here (not applied) so all modules share one
// classloader for them. Module rules live in build-logic (ModuleRules.kt).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Unit tests of the build logic itself (e.g. the versionCode function, 06 §6).
// :app's testDebugUnitTest depends on this, so the normal unit-test run covers them.
tasks.register("buildLogicTest") {
    group = "verification"
    description = "Runs the unit tests of the build-logic convention plugins."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
}
