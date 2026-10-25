plugins {
    alias(libs.plugins.umamit.jvm.library)
}

dependencies {
    // The built-in styles (STY-01) are made of the shared recipe types.
    api(project(":core:model"))
}
