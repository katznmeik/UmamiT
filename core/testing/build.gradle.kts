plugins {
    alias(libs.plugins.umamit.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.core)
}
