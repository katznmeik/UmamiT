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
private val JVM_TARGET = JvmTarget.JVM_17

/** Shared Android settings (06 §1, D-023): compile/target 36, min 35, build-tools pinned. */
internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.int("compileSdk")
        buildToolsVersion = libs.string("buildTools")
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }
    // AGP 9 has built-in Kotlin support; it registers the `kotlin` extension.
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
    // Hilt/KSP generate sources into the unit-test variant even of modules without
    // tests yet; without this, Gradle 9 fails their empty test tasks.
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

/** Shared settings for the pure Kotlin/JVM modules. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
}
