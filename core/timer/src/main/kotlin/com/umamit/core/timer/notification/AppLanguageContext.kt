package com.umamit.core.timer.notification

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList

/**
 * [base] in the app's own language (GEN-10, SET-35; D-136 item 15), for the timer's notification texts and actions.
 *
 * Android keeps the per-app language for the package, but a process holds it only while it has an activity: when
 * the last activity of a process goes, the process's configuration falls back to the phone's language (Android 15
 * and 16, `WindowProcessController.unregisterActivityConfigurationListener` clears the process's override). The
 * timer posts from its service and its receivers, often with no activity: the app swiped away, the process started
 * by an alarm after a kill. So the resources here follow [LocaleManager.getApplicationLocales] at each lookup, and
 * the next post after a language change is in the new language. *System* (no app language) uses the process's
 * resources as they are; so does a failed lookup, as a notification must never fail over its language.
 */
internal class AppLanguageContext(base: Context) : ContextWrapper(base) {
    private val localeManager: LocaleManager? = base.getSystemService(LocaleManager::class.java)

    /** The resources of the last app language; a [Pair] so that the language and its resources change together. */
    @Volatile
    private var inLanguage: Pair<LocaleList, Resources>? = null

    override fun getResources(): Resources {
        val locales = runCatching { localeManager?.applicationLocales }.getOrNull()
        return if (locales == null || locales.isEmpty) baseContext.resources else resourcesIn(locales)
    }

    private fun resourcesIn(locales: LocaleList): Resources = inLanguage?.takeIf { it.first == locales }?.second
        ?: createConfigurationContext(Configuration().apply { setLocales(locales) }).resources
            .also { inLanguage = locales to it }
}
