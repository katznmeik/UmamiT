package com.umamit.app

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * GEN-10 AC1: every `values/strings*.xml` entry of every module has a `values-en` entry (D-136 item 15,
 * #359), with the same format arguments. Lint's `MissingTranslation` says the same for the whole build; this test
 * names the module and the entry, and it also guards what lint cannot see.
 *
 * **The exclusion set (GEN-10 AC2).** The fixed tea vocabulary stays German in both languages: the Sorte names
 * (`sorte_*`), the descriptors and their groups (`ui_descriptor_*`) and the taste-slider names (`ui_taste_<slider>`).
 * They carry `translatable="false"` in their `values/strings*.xml`, which is the one place that lists them; this test
 * skips them and fails when `values-en` redefines one.
 *
 * **Pending modules.** Run 2 of #359 (T3) translates the timer path. Until then these modules have no `values-en`
 * yet and are skipped; run 2 empties the list, and the test fails for a pending module that is complete already, so
 * the list cannot go stale.
 */
class TranslationCompletenessTest {
    /** Modules whose English is not done yet (#359 run 2). Remove each one when its `values-en` is complete. */
    private val pendingModules = setOf("feature/brew", "core/timer", "core/backup")

    private val repoRoot = File(System.getProperty("user.dir")).parentFile

    @Test
    fun everyDefaultStringHasAnEnglishValueWithTheSameArguments() {
        val problems = mutableListOf<String>()
        for (module in modules()) {
            val missing = problemsOf(module)
            if (module in pendingModules) {
                assertWithMessage("$module is in the pending list but its English is complete: remove it from the list")
                    .that(missing)
                    .isNotEmpty()
            } else {
                problems += missing.map { "$module: $it" }
            }
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

    @Test
    fun pendingModulesExist() {
        val unknown = pendingModules - modules().toSet()
        assertWithMessage("pending modules without a values/strings.xml: $unknown").that(unknown).isEmpty()
    }

    private fun modules(): List<String> {
        val roots = listOf("app") + listOf("core", "feature").flatMap { group ->
            File(repoRoot, group).listFiles().orEmpty().filter { it.isDirectory }.map { "$group/${it.name}" }
        }
        return roots.filter { File(repoRoot, "$it/src/main/res/values/strings.xml").isFile }.sorted()
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
        val files = dir.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty()
        for (file in files.sortedBy { it.name }) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
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
        val arguments = ARGUMENT.findAll(text).map { it.value }.toSet()
        return arguments.sorted().joinToString(",")
    }

    private companion object {
        private val ARGUMENT = Regex("%(?:\\d+\\$)?[sd]")
    }
}
