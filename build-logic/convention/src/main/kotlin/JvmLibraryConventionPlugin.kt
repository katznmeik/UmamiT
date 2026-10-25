import com.umamit.buildlogic.ModuleRules
import com.umamit.buildlogic.configureKotlinJvm
import com.umamit.buildlogic.lib
import com.umamit.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module (model, common, domain, timer-engine, catalog-parser): no Android allowed. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        ModuleRules.enforcePure(this)
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
