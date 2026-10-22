import org.gradle.plugin.use.PluginDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.umamit.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // compileOnly: the root build puts these plugins on the classpath (root build.gradle.kts).
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    // Quality tooling (M1-02): resolved through the plugin marker of the catalog's
    // plugin alias, so the version stays single-sourced in libs.versions.toml.
    compileOnly(libs.plugins.spotless.marker())
    compileOnly(libs.plugins.detekt.marker())
    compileOnly(libs.plugins.kover.marker())
    compileOnly(libs.plugins.roborazzi.marker())

    testImplementation(libs.junit4)
}

/** Maven coordinates of a plugin's marker artifact; it depends on the plugin implementation. */
fun Provider<PluginDependency>.marker(): Provider<String> =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
    }
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "umamit.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "umamit.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "umamit.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "umamit.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "umamit.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("hilt") {
            id = "umamit.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidRoom") {
            id = "umamit.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("root") {
            id = "umamit.root"
            implementationClass = "RootConventionPlugin"
        }
    }
}
