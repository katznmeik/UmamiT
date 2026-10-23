package com.umamit.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/** Aggregate task name; runs the check for every variant (debug and release). */
const val CHECK_MANIFEST_PERMISSIONS_TASK = "checkManifestPermissions"

/**
 * Registers `check<Variant>ManifestPermissions` for each app variant and the
 * aggregate [CHECK_MANIFEST_PERMISSIONS_TASK] (also part of `check`). Each task
 * reads the variant's merged manifest and fails on any permission outside
 * [ManifestPermissions] (06 §5.7).
 */
internal fun Project.configureManifestPermissionCheck() {
    val aggregate = tasks.register(CHECK_MANIFEST_PERMISSIONS_TASK) {
        group = "verification"
        description = "Fails if a merged app manifest requests a permission not listed in 06 §5.7."
    }
    tasks.named("check") { dependsOn(aggregate) }

    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            val variantTask = tasks.register<CheckManifestPermissionsTask>(
                "check${variant.name.replaceFirstChar(Char::titlecase)}ManifestPermissions",
            ) {
                group = "verification"
                description = "Checks the merged ${variant.name} manifest against 06 §5.7."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                applicationId.set(variant.applicationId)
                allowedPermissions.set(ManifestPermissions.allowedFor(debugBuildType = variant.buildType == "debug"))
                report.set(layout.buildDirectory.file("reports/manifest-permissions/${variant.name}.txt"))
            }
            aggregate.configure { dependsOn(variantTask) }
        }
    }
}

@CacheableTask
abstract class CheckManifestPermissionsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val manifest = mergedManifest.get().asFile
        val violations = ManifestPermissions.violations(
            manifestXml = manifest.readText(),
            applicationId = applicationId.get(),
            allowed = allowedPermissions.get(),
        )
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The merged manifest requests permissions not allowed by 06 §5.7:")
                    violations.forEach { appendLine("  - $it") }
                    appendLine("Manifest: $manifest")
                    append(
                        "Remove the permission (use tools:node=\"remove\" for one added by a library) " +
                            "or change 06 §5.7 and ManifestPermissions together.",
                    )
                },
            )
        }
        report.get().asFile.writeText("OK: no permission outside 06 §5.7 in ${applicationId.get()}\n")
    }
}
