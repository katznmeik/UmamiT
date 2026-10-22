import com.android.build.api.dsl.LibraryExtension
import com.umamit.buildlogic.ModuleRules
import com.umamit.buildlogic.configureKotlinAndroid
import com.umamit.buildlogic.derivedNamespace
import com.umamit.buildlogic.lib
import com.umamit.buildlogic.libs
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module; namespace derived from the project path. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (path in ModuleRules.PURE_MODULES) {
            throw GradleException("Module rule violated: pure module $path must not be an Android library (06 §3).")
        }
        if (path.startsWith(":feature:")) ModuleRules.enforceFeature(this)

        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = derivedNamespace
            configureKotlinAndroid(this)
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
