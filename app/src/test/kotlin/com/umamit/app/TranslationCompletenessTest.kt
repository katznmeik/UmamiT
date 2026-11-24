package com.umamit.app

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * GEN-10 AC1: every text entry (`string`, `plurals`, `string-array`) in any file of `values` of every module has a
 * `values-en` entry (D-136 item 15, #359), with the same format arguments (every Java format specifier: `%1$s`,
 * `%.1f`, `%02d`, `%,d` …; `%%` and `%n` are no argument). Lint's `MissingTranslation` says the same for the whole
 * build; this test names the module and the entry, and it also guards what lint cannot see.
 *
 * **The exclusion set (GEN-10 AC2).** The fixed tea vocabulary stays German in both languages: the Sorte names
 * (`sorte_*`), the descriptors and their groups (`ui_descriptor_*`) and the taste-slider names (`ui_taste_<slider>`).
 * They carry `translatable="false"` in their `values/strings*.xml`, which is the one place that lists them; this test
 * skips them and fails when `values-en` redefines one.
 *
 * Every module is checked: run 2 of #359 translated the timer path (`feature/brew`, `core/timer`, `core/backup`), the
 * last modules that were pending.
 */
class TranslationCompletenessTest {
    private val repoRoot = File(System.getProperty("user.dir")).parentFile

    @Test
    fun everyDefaultStringHasAnEnglishValueWithTheSameArguments() {
        val problems = mutableListOf<String>()
        for (module in modules()) {
            problems += problemsOf(module).map { "$module: $it" }
        }
        assertWithMessage("English is incomplete:\n" + problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun theVocabularyStaysGermanInEnglish() {
        val vocabulary = modules().flatMap { module ->
            read(module, "values").filter { it.value.untranslatable }.keys.map { module to it }
        }
        assertWithMessage("the fixed tea vocabulary (GEN-10 AC2) is missing").that(vocabulary).isNotEmpty()
        val translated = vocabulary.filter { (module, name) -> name in read(module, "values-en") }
        assertWithMessage(
            "translatable=\"false\" entries redefined in values-en: $translated",
        ).that(translated).isEmpty()
    }

    private fun modules(): List<String> {
        val roots = listOf("app") + listOf("core", "feature").flatMap { group ->
            File(repoRoot, group).listFiles().orEmpty().filter { it.isDirectory }.map { "$group/${it.name}" }
        }
        // Every module with texts in either folder, whatever its files are called (a module with only plurals, or
        // with an English file and no default one, is checked too).
        return roots.filter { read(it, "values").isNotEmpty() || read(it, "values-en").isNotEmpty() }.sorted()
    }

    private fun problemsOf(module: String): List<String> {
        val default = read(module, "values")
        val english = read(module, "values-en")
        val problems = mutableListOf<String>()
        for ((name, entry) in default) {
            if (entry.untranslatable) continue
            val translation = english[name]
            when {
                translation == null -> problems += "$name has no English value"

                translation.kind != entry.kind -> problems += "$name is a ${translation.kind} in English"

                translation.signature != entry.signature ->
                    problems += "$name: the format arguments differ (${entry.signature} vs ${translation.signature})"
            }
        }
        problems += (english.keys - default.keys).map { "$it is only in values-en" }
        return problems
    }

    private class Raw(val kind: String, val signature: String, val untranslatable: Boolean)

    private fun read(module: String, folder: String): Map<String, Raw> {
        val dir = File(repoRoot, "$module/src/main/res/$folder")
        val result = linkedMapOf<String, Raw>()
        val files = dir.listFiles { file -> file.extension == "xml" }.orEmpty()
        for (file in files.sortedBy { it.name }) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = document.documentElement.childNodes
            val texts = (0 until children.length).mapNotNull { children.item(it) as? Element }
            for (element in texts.filter { it.tagName in TEXT_TAGS }) {
                val name = element.getAttribute("name")
                val untranslatable = element.getAttribute("translatable") == "false"
                result[name] = Raw(element.tagName, argumentsOf(element), untranslatable)
            }
        }
        return result
    }

    /** The sorted distinct format arguments of an entry, e.g. `%1$d,%2$s`; plurals compare the union of their items. */
    private fun argumentsOf(element: Element): String {
        val text = element.textContent
        val arguments = SPECIFIER.findAll(text).map { it.value }.filter { it != "%%" && it != "%n" }.toSet()
        return arguments.sorted().joinToString(",")
    }

    private companion object {
        /** A `java.util.Formatter` specifier: index, flags, width, precision and conversion, `%%` included. */
        private val SPECIFIER =
            Regex("%(?:\\d+\\$|<)?[-#+ 0,(]*\\d*(?:\\.\\d+)?(?:[tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])")

        private val TEXT_TAGS = setOf("string", "plurals", "string-array")
    }
}
