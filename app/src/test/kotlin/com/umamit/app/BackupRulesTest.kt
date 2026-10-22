package com.umamit.app

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android device backup (05 §8): cloud backup and device transfer carry the
 * personal database `umamit.db` (with its `-wal`/`-shm` files) and the
 * DataStore settings, and nothing else: not `catalog.db*`, not `photos/`, not
 * `catalog/`, not caches. Checked on the XML the manifest references, with
 * Android's include/exclude semantics.
 */
class BackupRulesTest {
    private val manifest = parse("src/main/AndroidManifest.xml")
    private val application = manifest.getElementsByTagName("application").item(0) as Element

    /** The rules of one backup kind: backed up = matched by an include and by no exclude. */
    private class Rules(
        private val includes: List<Pair<String, String>>,
        private val excludes: List<Pair<String, String>>,
    ) {
        fun backsUp(domain: String, path: String): Boolean =
            includes.any { covers(it, domain, path) } && excludes.none { covers(it, domain, path) }

        private fun covers(rule: Pair<String, String>, domain: String, path: String): Boolean {
            val (ruleDomain, rulePath) = rule
            if (ruleDomain != domain) return false
            val dir = rulePath.trimEnd('/')
            return rulePath == "." || path == dir || path.startsWith("$dir/")
        }

        companion object {
            fun of(section: Element) = Rules(section.rules("include"), section.rules("exclude"))

            private fun Element.rules(tag: String): List<Pair<String, String>> =
                getElementsByTagName(tag).elements().map { it.getAttribute("domain") to it.getAttribute("path") }
        }
    }

    private fun xmlResource(attribute: String): Element {
        val reference = application.getAttribute(attribute)
        assertWithMessage("manifest $attribute").that(reference).startsWith("@xml/")
        return parse("src/main/res/xml/${reference.removePrefix("@xml/")}.xml").documentElement
    }

    /** Every rule set the system may use on API 35/36: cloud backup, device transfer, and the legacy full backup. */
    private fun allRuleSets(): Map<String, Rules> {
        val extraction = xmlResource("android:dataExtractionRules")
        assertThat(extraction.tagName).isEqualTo("data-extraction-rules")
        val fullBackup = xmlResource("android:fullBackupContent")
        assertThat(fullBackup.tagName).isEqualTo("full-backup-content")
        return mapOf(
            "cloud-backup" to Rules.of(extraction.single("cloud-backup")),
            "device-transfer" to Rules.of(extraction.single("device-transfer")),
            "full-backup-content" to Rules.of(fullBackup),
        )
    }

    @Test
    fun `backup is allowed and both rule files are referenced from the manifest`() {
        assertThat(application.getAttribute("android:allowBackup")).isEqualTo("true")
        assertThat(allRuleSets()).hasSize(3)
    }

    @Test
    fun `the personal database and the settings are backed up`() {
        allRuleSets().forEach { (kind, rules) ->
            listOf("umamit.db", "umamit.db-wal", "umamit.db-shm").forEach { file ->
                assertWithMessage("$kind: database/$file").that(rules.backsUp("database", file)).isTrue()
            }
            assertWithMessage("$kind: DataStore settings")
                .that(rules.backsUp("file", "datastore/settings.json")).isTrue()
        }
    }

    @Test
    fun `catalog, photos, caches and other databases are not backed up`() {
        val notBackedUp = listOf(
            "database" to "catalog.db",
            "database" to "catalog.db-wal",
            "database" to "catalog.db-shm",
            // e.g. LeakCanary's database in debug builds: not personal data.
            "database" to "leaks.db",
            "file" to "photos/0b7c.jpg",
            "file" to "catalog/TG0213.webp",
            "root" to "cache/image_cache/x",
            "sharedpref" to "anything.xml",
            "external" to "anything",
        )
        allRuleSets().forEach { (kind, rules) ->
            notBackedUp.forEach { (domain, path) ->
                assertWithMessage("$kind: $domain/$path").that(rules.backsUp(domain, path)).isFalse()
            }
        }
    }

    @Test
    fun `the diagnostics log is not backed up`() {
        // DataModule keeps it in Context.noBackupFilesDir (06 §5.6), which the system never backs
        // up; no rule may include it either way, nor the same log under filesDir.
        val log = listOf(
            "root" to "no_backup/diagnostics/diagnostics.log",
            "root" to "no_backup/diagnostics/diagnostics.1.log",
            "file" to "diagnostics/diagnostics.log",
        )
        allRuleSets().forEach { (kind, rules) ->
            log.forEach { (domain, path) ->
                assertWithMessage("$kind: $domain/$path").that(rules.backsUp(domain, path)).isFalse()
            }
        }
    }

    private companion object {
        fun parse(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(File(path))

        fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

        fun Element.single(tag: String): Element = getElementsByTagName(tag).elements().single()
    }
}
