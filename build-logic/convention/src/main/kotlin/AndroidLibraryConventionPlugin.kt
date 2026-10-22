import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.umamit.buildlogic.ModuleRules
import com.umamit.buildlogic.configureKotlinAndroid
import com.umamit.buildlogic.derivedNamespace
import com.umamit.buildlogic.lib
import com.umamit.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android library module; namespace derived from the project path. A device-test
 * APK (`src/androidTest/`) is only built when the module has instrumented tests,
 * so `connectedDebugAndroidTest` in the CI emulator job does not build, install
 * and run an empty test APK for every library module.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (path in ModuleRules.PURE_MODULES) {
            ModuleRules.violation("pure module $path must not be an Android library")
        }
        if (path.startsWith(":feature:")) ModuleRules.enforceFeature(this)

        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = derivedNamespace
            configureKotlinAndroid(this)
        }
        val hasDeviceTests = file("src/androidTest").isDirectory
        extensions.configure<LibraryAndroidComponentsExtension> {
            beforeVariants { variant ->
                variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = hasDeviceTests
            }
        }
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
