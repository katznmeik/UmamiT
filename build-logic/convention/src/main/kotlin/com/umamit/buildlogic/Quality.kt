package com.umamit.buildlogic

import com.android.build.api.dsl.Lint
import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/*
 * Quality gates of 06 §7 (M1-02), applied by every module convention plugin so
 * all modules are checked the same way. The configuration files live in
 * `config/` at the repository root; there are no baselines.
 */

/** Spotless with ktlint (Kotlin official style via `.editorconfig`) for the module's sources and build script. */
internal fun Project.configureSpotless(
    kotlinTargets: List<String> = listOf("src/**/*.kt"),
    kotlinGradleTargets: List<String> = listOf("*.kts"),
) {
    pluginManager.apply("com.diffplug.spotless")
    val ktlintVersion = libs.string("ktlint")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target(kotlinTargets)
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target(kotlinGradleTargets)
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
        }
    }
}

/**
 * Detekt with the Compose rules, on top of Detekt's default configuration
 * (`config/detekt/detekt.yml` holds only the deviations).
 *
 * Detekt 2 (alpha) is used because it is built with Kotlin 2.4 like this build:
 * Detekt 1.23.x embeds the Kotlin 2.0 compiler (it cannot parse newer syntax and
 * needs its Kotlin artifacts pinned back), and the current compose-rules only
 * support the Detekt 2 API. The `detekt` task (no type resolution) is the gate;
 * the per-source-set tasks with type resolution are not part of it yet. Its
 * default sources are only `src/{main,test}/{java,kotlin}`, so Android modules
 * pass [includeDeviceTests] to check the instrumented tests too.
 */
internal fun Project.configureDetekt(sources: List<Any>? = null, includeDeviceTests: Boolean = false) {
    pluginManager.apply("dev.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        if (sources != null) source.setFrom(sources)
        if (includeDeviceTests) source.from("src/androidTest/java", "src/androidTest/kotlin")
    }
    dependencies {
        add("detektPlugins", libs.lib("compose-rules-detekt"))
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set(JVM_TARGET.target)
        reports {
            html.required.set(true)
            sarif.required.set(true)
            checkstyle.required.set(false)
            markdown.required.set(false)
        }
    }
}

/**
 * Android Lint: no baseline, every warning is an error. Checks whose result
 * depends on the network or the date (newer library versions) and deliberate
 * project decisions are switched off in `config/lint/lint.xml`, with reasons.
 */
internal fun Project.configureLint(lint: Lint) {
    lint.apply {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = false
        baseline = null
        lintConfig = rootProject.file("config/lint/lint.xml")
        explainIssues = false
    }
}

/** Lint for pure Kotlin/JVM modules (the standalone `com.android.lint` plugin; no Android dependency). */
internal fun Project.configureJvmLint() {
    pluginManager.apply("com.android.lint")
    extensions.configure<Lint> { configureLint(this) }
}

/**
 * Kover coverage for the pure modules (06 §7: ≥ 90 % line coverage in the
 * logic modules). Reports only for now; the verification rule is added with
 * the first real code (TODO(M1-08): `minBound(90)` for `core:timer-engine`).
 */
internal fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
}
