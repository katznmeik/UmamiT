package com.umamit.testlocale

import org.robolectric.annotation.Config
import org.robolectric.pluginapi.config.GlobalConfigProvider
import org.robolectric.plugins.ConfigConfigurer
import org.robolectric.plugins.PackagePropertiesLoader
import org.robolectric.util.inject.Supersedes

/**
 * Every Robolectric test of every Android module runs in German unless it asks for a language (GEN-10, D-136
 * item 15). Robolectric's own default is en-US, and a class's `@Config(qualifiers = "w411dp-…")` replaces any
 * default qualifier, so this replaces Robolectric's [ConfigConfigurer] and appends `+de-rDE` to every merged
 * qualifier string that names no language: the resources' configuration and `Locale.getDefault()` are then German.
 * An English test names it, e.g. `@Config(qualifiers = "en-rUS-w411dp-h891dp-xhdpi")` or `"+en-rUS"`.
 *
 * Registered through `META-INF/services`; build-logic adds `config/test-locale` to the unit-test sources of every
 * Android module.
 */
@Supersedes(ConfigConfigurer::class)
class GermanConfigConfigurer(
    packagePropertiesLoader: PackagePropertiesLoader,
    globalConfigProvider: GlobalConfigProvider,
) : ConfigConfigurer(packagePropertiesLoader, globalConfigProvider) {

    override fun defaultConfig(): Config = german(super.defaultConfig())

    override fun merge(parentConfig: Config, childConfig: Config): Config =
        german(super.merge(parentConfig, childConfig))

    private fun german(config: Config): Config {
        val qualifiers = config.qualifiers
        if (NAMED_LANGUAGE.containsMatchIn(qualifiers)) return config
        val pinned = if (qualifiers.isBlank()) GERMAN else "$qualifiers +$GERMAN"
        return Config.Builder(config).setQualifiers(pinned).build()
    }

    private companion object {
        const val GERMAN = "de-rDE"

        /** A German or English locale qualifier, alone or with its region („de", „en-rUS", „+en"). */
        val NAMED_LANGUAGE = Regex("""(^|[ +-])(de|en)(-r[A-Z]{2})?($|[ -])""")
    }
}
