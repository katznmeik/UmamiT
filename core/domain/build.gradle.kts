plugins {
    alias(libs.plugins.umamit.jvm.library)
}

dependencies {
    // The catalog list's filters work on the shared catalog models (CAT-10).
    api(project(":core:model"))
}
