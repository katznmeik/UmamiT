package com.umamit.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("Library '$alias' missing in libs.versions.toml") }

internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Version '$alias' missing in libs.versions.toml") }
        .requiredVersion.toInt()

internal fun VersionCatalog.string(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("Version '$alias' missing in libs.versions.toml") }
        .requiredVersion

/** Namespace derived from the project path, e.g. `:core:timer-engine` → `com.umamit.core.timerengine`. */
internal val Project.derivedNamespace: String
    get() = "com.umamit." + path.removePrefix(":").replace(':', '.').replace("-", "")
