package com.umamit.feature.onboarding.howto

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The name check (D-137 item 26, as #361 did): every in-app name the Anleitungen' copy quotes in italics
 * (03 SCR-36, `*…*`) equals a string resource of that language, so a renamed button fails here instead of leaving
 * the help stale. The names are listed below per topic, next to nothing else: the copy itself holds them as plain
 * text, so the first test checks that each listed name occurs in its topic's copy. A name matches a resource's
 * value exactly, or, for a format string such as „Alle %1$d Einträge anzeigen", by its literal prefix.
 *
 * The resources are read from every module's `src/main/res` (the unit test has no other module's `R`); English
 * falls back to the default (German) file for strings that are not translated.
 */
class HowToNamesTest {

    @Test
    fun everyListedNameOccursInItsTopicsCopy() {
        val problems = mutableListOf<String>()
        LANGUAGES.forEach { language ->
            val onboarding = Resources.values(language)
            language.names.forEach { (topic, names) ->
                val copy = onboarding.filterKeys { it.startsWith("howto_${topic}_") }.values.joinToString("\n")
                names.filter { plain(it) !in plain(copy) }.forEach {
                    problems +=
                        "${language.tag} topic $topic: „$it\" is not in the copy"
                }
                assertWithMessage("topic $topic has copy").that(copy).isNotEmpty()
            }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun everyQuotedNameIsAStringResourceOfItsLanguage() {
        val problems = mutableListOf<String>()
        LANGUAGES.forEach { language ->
            val resources = Resources.appValues(language).map(::plain)
            val exact = resources.toSet()
            val prefixes = resources.filter {
                '%' in it
            }.map { it.substringBefore('%').trim() }.filter { it.isNotEmpty() }
            language.names.forEach { (topic, names) ->
                names.forEach { name ->
                    val shown = plain(name)
                    val matches = shown in exact || prefixes.any { shown.startsWith(it) }
                    if (!matches) problems += "${language.tag} topic $topic: „$name\" is no string resource"
                }
            }
        }
        assertWithMessage("\n" + problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun everyTopicHasAllItsCopyInBothLanguages() {
        val problems = mutableListOf<String>()
        HowToTopic.entries.forEachIndexed { index, topic ->
            val prefix = "howto_${index + 1}_"
            LANGUAGES.forEach { language ->
                val keys = Resources.values(language).keys.filter { it.startsWith(prefix) }
                val expected = 3 + topic.steps.size + (if (topic.tip != null) 1 else 0)
                if (keys.size !=
                    expected
                ) {
                    problems += "${language.tag} ${topic.name}: ${keys.size} strings, expected $expected"
                }
            }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }
}

/** The quoted names of each topic (SCR-36 copy, the italics), by language. */
private val QUOTED_NAMES_DE: Map<Int, List<String>> = mapOf(
    1 to listOf(
        "Sammlung",
        "Meine Tees",
        "Tee ohne Katalog hinzufügen",
        "Sorte wählen",
        "Andere / unbekannt",
        "Neuer Tee",
        "Kauf",
        "Packung",
        "Preis",
        "Speichern",
        "+ Foto",
    ),
    2 to listOf(
        "Sammlung",
        "Katalog",
        "Link einfügen",
        "Tee laden",
        "In Sammlung aufnehmen",
        "Packung",
        "Preis",
        "Kaufdatum",
        "Aufnehmen",
        "Nachkauf erfassen",
        "Katalog aktualisieren",
    ),
    3 to listOf(
        "Meine Tees",
        "Vorrat",
        "Nachkauf erfassen",
        "Bestand korrigieren",
        "Kauf",
        "Bestand korrigiert",
        "Speichern",
        "Einstellungen",
        "Aufguss & Vorrat",
        "Knapp ab",
        "Vorrats-Erinnerung",
        "Bestand abziehen",
    ),
    4 to listOf(
        "Rezepte",
        "Rezept hinzufügen",
        "Als Standard",
        "Aufguss",
        "Spülen",
        "Aufschlagen",
        "Aufguss hinzufügen",
        "Speichern",
        "Rezept teilen",
        "Meine Tees",
        "Rezept importieren",
        "Rezept übernehmen",
        "Zeiten ins Rezept übernehmen",
    ),
    5 to listOf(
        "Aufguss starten",
        "Zuletzt aufgegossen",
        "Neue Session",
        "Gefäß",
        "Verhältnis halten",
        "Session starten",
        "Start",
        "Abgegossen",
        "Schritte bearbeiten",
        "Weiterer Aufguss",
        "Session beenden",
        "Schnelltimer",
        "Einstellungen",
        "Teegeschirr",
    ),
    6 to listOf(
        "Session beenden",
        "Gespeichert im Journal",
        "Tee",
        "Zubereitung",
        "Journal",
        "Geschmack hinzufügen",
        "+ Beschreibung",
        "Zeiten ins Rezept übernehmen",
        "Bearbeiten",
        "So nochmal aufgießen",
    ),
    7 to listOf(
        "Einstellungen",
        "Daten & Sicherung",
        "Sicherung erstellen",
        "An Sicherung erinnern",
        "Sicherung einspielen",
        "Import rückgängig machen",
        "Journal als CSV",
        "Alle Daten löschen",
    ),
    8 to listOf(
        "Einstellungen",
        "Timer-Zuverlässigkeit",
        "Beheben",
        "Benachrichtigungen",
        "Vollbild-Alarm",
        "Akku: Uneingeschränkt",
        "Exakte Alarme",
        "Vollbild-Alarm wieder erlauben",
        "Erlauben",
        "Der Timer ist eventuell unzuverlässig",
        "Timer & Alarm",
        "Alarm testen",
        "Bei „Nicht stören\" klingeln",
    ),
)

private val QUOTED_NAMES_EN: Map<Int, List<String>> = mapOf(
    1 to listOf(
        "Collection",
        "My teas",
        "Add a tea without the catalog",
        "Choose variety",
        "Andere / unbekannt",
        "New tea",
        "Package",
        "Price",
        "Purchase",
        "Save",
        "+ Photo",
    ),
    2 to listOf(
        "Paste link",
        "Collection",
        "Catalog",
        "Load tea",
        "Add to collection",
        "Package",
        "Price",
        "Purchase date",
        "Add",
        "Log a repurchase",
        "Update catalog",
    ),
    3 to listOf(
        "My teas",
        "Stock",
        "Log a repurchase",
        "Correct stock",
        "Purchase",
        "Stock corrected",
        "Save",
        "Settings",
        "Brewing & stock",
        "Low from",
        "Stock reminder",
        "Deduct stock",
    ),
    4 to listOf(
        "Add recipe",
        "Recipes",
        "As default",
        "Infusion",
        "Rinse",
        "Whisk",
        "Add infusion",
        "Save",
        "Share recipe",
        "My teas",
        "Import recipe",
        "Take over recipe",
        "Save times to the recipe",
    ),
    5 to listOf(
        "Start brewing",
        "Recently brewed",
        "New session",
        "Vessel",
        "Keep ratio",
        "Start session",
        "Start",
        "Poured",
        "Edit steps",
        "Another infusion",
        "End session",
        "Quick timer",
        "Settings",
        "Teaware",
    ),
    6 to listOf(
        "End session",
        "Saved to the journal",
        "Tea",
        "Brewing",
        "Journal",
        "Add taste",
        "+ Descriptor",
        "Save times to the recipe",
        "Edit",
        "Brew this again",
    ),
    7 to listOf(
        "Settings",
        "Data & backup",
        "Create backup",
        "Remind me to back up",
        "Restore backup",
        "Undo restore",
        "Journal as CSV",
        "Delete all data",
    ),
    8 to listOf(
        "Settings",
        "Timer reliability",
        "Fix",
        "Notifications",
        "Full-screen alarm",
        "Battery: Unrestricted",
        "Exact alarms",
        "Allow the full-screen alarm again",
        "Allow",
        "The timer may be unreliable",
        "Timer & alarm",
        "Test alarm",
        "Ring under “Do Not Disturb”",
    ),
)

private const val NBSP = '\u00A0'

/** A language of the app: where its strings live and the names its copy quotes. */
private class Language(val tag: String, val directory: String, val names: Map<Int, List<String>>)

private val LANGUAGES = listOf(
    Language("de", "values", QUOTED_NAMES_DE),
    Language("en", "values-en", QUOTED_NAMES_EN),
)

/** The string resources, read from the XML files of every module (Gradle runs unit tests in the module's folder). */
private object Resources {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val files: List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" && "src/main/res" in it.path }
        .toList()

    /** The strings of the onboarding module in [language] (the Anleitungen's copy). */
    fun values(language: Language): Map<String, String> = read(language) { "feature/onboarding" in it.path }

    /** The values of the app's strings in [language], other than the onboarding module's own (copies and copy). */
    fun appValues(language: Language): List<String> =
        read(language) { "feature/onboarding" !in it.path }.values.toList()

    private fun read(language: Language, include: (File) -> Boolean): Map<String, String> {
        val result = linkedMapOf<String, String>()
        // The default file first, then the language's own over it.
        listOf("values", language.directory).distinct().forEach { directory ->
            files.filter { it.parentFile?.name == directory && include(it) }.forEach { file ->
                STRING.findAll(file.readText()).forEach { result[it.groupValues[1]] = unescape(it.groupValues[2]) }
            }
        }
        return result
    }

    private val STRING = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun unescape(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\u00A0", NBSP.toString())
        .replace("&#160;", NBSP.toString())
        .replace("\\n", "\n")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Quote styles and no-break spaces are typography, not names: the spec writes „…" where the app has „…“. */
private fun plain(text: String): String = text.replace(NBSP, ' ').replace('“', '"').replace('”', '"')
