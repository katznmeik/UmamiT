import com.android.build.api.dsl.ApplicationExtension
import com.umamit.buildlogic.configureKotlinAndroid
import com.umamit.buildlogic.int
import com.umamit.buildlogic.libs
import com.umamit.buildlogic.versionCodeOf
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The app module (06 §6):
 * - version from `gradle.properties` (`umamit.version`), versionCode via [versionCodeOf];
 * - `debug` with the `.debug` suffix; `release` with R8 and resource shrinking;
 * - release signing only from the environment (`UMAMIT_KEYSTORE_FILE`,
 *   `UMAMIT_KEYSTORE_PASSWORD`; PKCS12, alias `umamit`, one password for store
 *   and key). Without them the release build stays unsigned.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        val appVersion = providers.gradleProperty("umamit.version").orNull
            ?: error("gradle.properties must define umamit.version (06 §6)")
        val keystoreFile = providers.environmentVariable(ENV_KEYSTORE_FILE).orNull?.takeIf { it.isNotBlank() }
        val keystorePassword = providers.environmentVariable(ENV_KEYSTORE_PASSWORD).orNull?.takeIf { it.isNotBlank() }

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                targetSdk = libs.int("targetSdk")
                versionName = appVersion
                versionCode = versionCodeOf(appVersion)
            }

            if (keystoreFile != null && keystorePassword != null) {
                signingConfigs.create("release") {
                    storeFile = file(keystoreFile)
                    storePassword = keystorePassword
                    storeType = "pkcs12"
                    keyAlias = KEY_ALIAS
                    keyPassword = keystorePassword
                }
            } else if (keystoreFile != null || keystorePassword != null) {
                logger.warn("UmamiT: only one of $ENV_KEYSTORE_FILE / $ENV_KEYSTORE_PASSWORD is set; release stays unsigned.")
            }

            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                }
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    signingConfig = signingConfigs.findByName("release")
                }
            }
        }
    }

    private companion object {
        const val ENV_KEYSTORE_FILE = "UMAMIT_KEYSTORE_FILE"
        const val ENV_KEYSTORE_PASSWORD = "UMAMIT_KEYSTORE_PASSWORD"
        const val KEY_ALIAS = "umamit"
    }
}
