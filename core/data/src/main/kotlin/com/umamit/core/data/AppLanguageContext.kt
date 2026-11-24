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
 * [base] in the app's own language (GEN-10 AC1; D-136, „A process without an activity"), for texts built without an
 * activity: the catalog refresh's, the low-stock and the backup reminders' notifications and their channels.
 *
 * Android keeps the per-app language for the package, but a process holds it only while it has an activity: when the
 * last activity of a process goes, the process's configuration falls back to the phone's language (Android 15 and
 * 16). So the resources here follow [LocaleManager.getApplicationLocales] at each lookup. *System* (no app language)
 * uses the process's resources as they are; so does a failed lookup, as a notification must never fail over its
 * language. The model is `core:timer`'s own `AppLanguageContext` for the timer's notifications.
 */
class AppLanguageContext private constructor(base: Context) : ContextWrapper(base) {
    private val localeManager: LocaleManager? =
        runCatching { base.getSystemService(LocaleManager::class.java) }.getOrNull()

    /** The resources of the last app language; a [Pair] so that the language and its resources change together. */
    @Volatile
    private var inLanguage: Pair<LocaleList, Resources>? = null

    /**
     * The language for numbers and dates in these texts: `AppLanguage.current()` reads the JVM's default locale,
     * which is the phone's language in a process without an activity.
     */
    val appLanguage: AppLanguage
        get() = AppLanguage.of(resources.configuration.locales[0])

    override fun getResources(): Resources {
        val locales = runCatching { localeManager?.applicationLocales }.getOrNull()
        return if (locales == null || locales.isEmpty) {
            baseContext.resources
        } else {
            runCatching { resourcesIn(locales) }.getOrElse { baseContext.resources }
        }
    }

    // AppBundleLocaleChanges: the app ships as one APK from GitHub Releases, not as a bundle split by language, and
    // that APK keeps both languages (`localeFilters`, D-136 item 15), so no language has to be downloaded.
    @SuppressLint("AppBundleLocaleChanges")
    private fun resourcesIn(locales: LocaleList): Resources = inLanguage?.takeIf { it.first == locales }?.second
        ?: createConfigurationContext(Configuration().apply { setLocales(locales) }).resources
            .also { inLanguage = locales to it }

    companion object {
        /** [context] in the app's language; [context] itself when it already is one. */
        fun of(context: Context): AppLanguageContext = context as? AppLanguageContext ?: AppLanguageContext(context)
    }
}
