package com.umamit.core.data

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.umamit.core.common.AppLanguage

/**
 * [base] in the app's own language (GEN-13 AC1; D-136, „A process without an activity"), for texts built without an
 * activity: the catalog refresh's, the low-stock and the backup reminders' notifications and their channels, and
 * `core:timer`'s notifications and channels.
 *
 * Android keeps the per-app language for the package, but a process holds it only while it has an activity: when the
 * last activity of a process goes, the process's configuration falls back to the phone's language (Android 15 and
 * 16). So the wrapper reads [LocaleManager.getApplicationLocales] once, when it is made: one snapshot for the text it
 * builds ([of] makes a new one per text, which sees a language changed since). *System* (no app language) uses the
 * process's resources as they are; so does a failed lookup, as a notification must never fail over its language.
 * This is the app's one copy: `core:timer` uses it too.
 */
class AppLanguageContext private constructor(base: Context) : ContextWrapper(base) {
    /** The app's resources in its own language, or the process's for *System* and on any failure. */
    private val inLanguage: Resources = run {
        val locales = runCatching { base.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
        if (locales == null || locales.isEmpty) {
            base.resources
        } else {
            runCatching { resourcesIn(locales) }.getOrElse { base.resources }
        }
    }

    /**
     * The language for numbers and dates in these texts: `AppLanguage.current()` reads the JVM's default locale,
     * which is the phone's language in a process without an activity.
     */
    val appLanguage: AppLanguage
        get() = AppLanguage.of(resources.configuration.locales[0])

    override fun getResources(): Resources = inLanguage

    // AppBundleLocaleChanges: the app ships as one APK from GitHub Releases, not as a bundle split by language, and
    // that APK keeps both languages (`localeFilters`, D-136 item 15), so no language has to be downloaded.
    @SuppressLint("AppBundleLocaleChanges")
    private fun resourcesIn(locales: LocaleList): Resources =
        createConfigurationContext(Configuration().apply { setLocales(locales) }).resources

    companion object {
        /** [context] in the app's language; [context] itself when it already is one. */
        fun of(context: Context): AppLanguageContext = context as? AppLanguageContext ?: AppLanguageContext(context)
    }
}
