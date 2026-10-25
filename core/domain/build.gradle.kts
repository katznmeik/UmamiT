plugins {
    alias(libs.plugins.umamit.jvm.library)
}

dependencies {
    // The catalog list's filters work on the shared catalog models (CAT-10).
    // The built-in styles (STY-01) are made of the shared recipe types.
    api(project(":core:model"))
}
