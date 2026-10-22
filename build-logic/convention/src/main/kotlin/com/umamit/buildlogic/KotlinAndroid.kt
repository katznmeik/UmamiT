package com.umamit.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val JAVA_VERSION = JavaVersion.VERSION_17
internal val JVM_TARGET = JvmTarget.JVM_17

/**
 * Shared Android settings (06 §1, D-023): compile/target 36, min 35, build-tools
 * pinned; plus the quality gates of 06 §7 (Spotless, Detekt, Lint).
 */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.int("compileSdk")
        buildToolsVersion = libs.string("buildTools")
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
        testOptions.unitTests.isIncludeAndroidResources = true
        configureLint(lint)
    }
    configureSpotless()
    configureDetekt(includeDeviceTests = true)
    // AGP 9 has built-in Kotlin support; it registers the `kotlin` extension.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
    // Hilt/KSP generate sources into the unit-test variant even of modules without
    // tests yet; without this, Gradle 9 fails their empty test tasks.
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
        // Robolectric's SDK 36 runtime needs these on JDK 21 ("Failed to interact
        // with raw FileDescriptor internals" otherwise).
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}

/** Shared settings for the pure Kotlin/JVM modules, including the quality gates of 06 §7. */
internal fun Project.configureKotlinJvm() {
    configureSpotless()
    configureDetekt()
    configureJvmLint()
    configureKover()
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
    // `./gradlew testDebugUnitTest` (CI, CLAUDE.md) selects tasks by name, so the
    // pure modules need it too; otherwise their tests would silently not run.
    tasks.register("testDebugUnitTest") {
        group = "verification"
        description = "Runs this pure module's unit tests (alias of `test`)."
        dependsOn("test")
    }
}
