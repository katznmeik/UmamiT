package com.umamit.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * Module rules of 06 §3, checked when a dependency or plugin is added, so a
 * violation fails every build at configuration time.
 */
internal object ModuleRules {

    /** Pure Kotlin/JVM modules: no Android dependency or plugin. */
    val PURE_MODULES = setOf(
        ":core:model",
        ":core:common",
        ":core:domain",
        ":core:timer-engine",
        ":core:catalog-parser",
    )

    private val ANDROID_PLUGIN_IDS = listOf(
        "com.android.base",
        "com.android.application",
        "com.android.library",
        "com.android.test",
        "com.android.dynamic-feature",
        "com.android.kotlin.multiplatform.library",
        "org.jetbrains.kotlin.android",
        "com.google.dagger.hilt.android",
        "androidx.room",
    )

    private val ANDROID_GROUP_PREFIXES = listOf(
        "androidx.",
        "android.",
        "com.android.",
        "com.google.android.",
    )

    /**
     * Configurations that hold build tools rather than the module's code (Lint,
     * Detekt, Kover; 06 §7). Their dependencies never reach the module's
     * classpath, so the module rules do not apply to them.
     */
    private val TOOL_CONFIGURATIONS = setOf("androidLintTool", "lintChecks", "detekt", "detektPlugins")
    private const val TOOL_CONFIGURATION_PREFIX = "kover"

    private fun isToolConfiguration(name: String) =
        name in TOOL_CONFIGURATIONS || name.startsWith(TOOL_CONFIGURATION_PREFIX)

    /** A pure module may not apply an Android plugin nor depend on Android artifacts or non-pure modules. */
    fun enforcePure(project: Project) {
        check(project.path in PURE_MODULES) {
            "${project.path} uses umamit.jvm.library but is not listed as a pure module in ModuleRules"
        }
        ANDROID_PLUGIN_IDS.forEach { id ->
            project.pluginManager.withPlugin(id) {
                violation("pure module ${project.path} must not apply the Android plugin '$id'")
            }
        }
        project.checkDependencies { configuration, dependency ->
            when (dependency) {
                is ProjectDependency -> if (dependency.path !in PURE_MODULES) {
                    violation(
                        "pure module ${project.path} must not depend on ${dependency.path} ($configuration); " +
                            "pure modules may only depend on other pure modules",
                    )
                }

                is ExternalDependency -> {
                    val group = dependency.group.orEmpty()
                    if (group == "android" || ANDROID_GROUP_PREFIXES.any { group.startsWith(it) }) {
                        violation(
                            "pure module ${project.path} must not depend on the Android artifact " +
                                "$group:${dependency.name} ($configuration)",
                        )
                    }
                }
            }
        }
    }

    /** Features depend only on core:* — in particular never on another feature (06 §3). */
    fun enforceFeature(project: Project) {
        project.checkDependencies { configuration, dependency ->
            if (dependency is ProjectDependency && !dependency.path.startsWith(":core:")) {
                val kind = if (dependency.path.startsWith(":feature:")) "feature → feature" else "feature → non-core"
                violation(
                    "($kind) ${project.path} must not depend on ${dependency.path} ($configuration); " +
                        "features depend only on core:* modules",
                )
            }
        }
    }

    /** Fails the build with a module-rule message that names 06 §3. */
    fun violation(message: String): Nothing = throw GradleException("Module rule violated: $message (06 §3).")

    /**
     * Runs [check] on every declared dependency of every code configuration
     * (not the [TOOL_CONFIGURATIONS]), except the module itself (AGP adds the
     * tested module to its test configurations).
     * Dependencies from the version catalog are added lazily, so they are
     * realised after evaluation to fail at configuration time.
     */
    private fun Project.checkDependencies(check: (configuration: String, dependency: Dependency) -> Unit) {
        configurations.matching { !isToolConfiguration(it.name) }.configureEach {
            val configuration = name
            dependencies.configureEach {
                if (!(this is ProjectDependency && path == this@checkDependencies.path)) check(configuration, this)
            }
        }
        afterEvaluate {
            configurations.forEach { it.dependencies.toList() }
        }
    }
}
