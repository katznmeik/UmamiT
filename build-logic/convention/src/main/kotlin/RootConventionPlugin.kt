import com.umamit.buildlogic.configureDetekt
import com.umamit.buildlogic.configureSpotless
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Root project: applies the quality gates of 06 §7 to the files that belong to
 * no module, i.e. the root build scripts and the build logic itself, so
 * `spotlessCheck` and `detekt` cover the whole repository.
 */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) { "umamit.root must be applied to the root project only" }
        configureSpotless(
            kotlinTargets = listOf("build-logic/convention/src/**/*.kt"),
            kotlinGradleTargets = listOf("*.kts", "build-logic/*.kts", "build-logic/convention/*.kts"),
        )
        configureDetekt(
            sources = listOf(
                file("build-logic/convention/src/main/kotlin"),
                file("build-logic/convention/src/test/kotlin"),
            ),
        )
    }
}
