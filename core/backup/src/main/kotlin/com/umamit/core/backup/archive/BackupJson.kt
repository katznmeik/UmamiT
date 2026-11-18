package com.umamit.core.backup.archive

import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.UserSettings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.ZoneId

/** The backup's JSON entries, read from a staged file: never a whole string, never a deep recursion. */
internal object BackupJson {
    /** The format needs 3 (`data.json`: object, array, row); a little room, never a recursion risk. */
    const val MAX_NESTING = 8

    /**
     * [file] as JSON: strict UTF-8 and nested at most [MAX_NESTING] deep, both checked in one streaming pass before
     * parsing (so a hostile file never drives the parser's recursion), then parsed from the stream. Damage otherwise.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun parse(file: File): JsonElement {
        ensure(nesting(file) <= MAX_NESTING)
        return decode(file) { FileInputStream(it).use { input -> BackupFormat.json.decodeFromStream(input) } }
    }

    private fun nesting(file: File): Int {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val counter = NestingCounter()
        try {
            InputStreamReader(FileInputStream(file), decoder).buffered().use { reader ->
                while (true) {
                    val code = reader.read()
                    if (code < 0) break
                    counter.feed(code.toChar())
                }
            }
        } catch (_: CharacterCodingException) {
            refuse(BackupRefusal.DAMAGED)
        }
        return counter.deepest
    }

    /** The deepest nesting of `{` and `[` outside strings, as the characters come. */
    private class NestingCounter {
        var deepest = 0
            private set
        private var depth = 0
        private var inString = false
        private var escaped = false

        fun feed(char: Char) {
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' || char == '[' -> deepest = maxOf(deepest, ++depth)
                char == '}' || char == ']' -> depth--
            }
        }
    }

    /**
     * [tree] decoded strictly with [BackupFormat.json]: every primitive of the JSON type its field has (the tree
     * decoder alone takes `"250"` for 250 and `"true"` for true), then unknown keys, unknown enum names, a missing key
     * without a default and a wrong structure fail. Any failure is damage.
     */
    fun <T> decodeStrict(serializer: KSerializer<T>, tree: JsonElement): T {
        checkTypes(tree, serializer.descriptor)
        return decode(tree) { BackupFormat.json.decodeFromJsonElement(serializer, it) }
    }

    /** Each primitive in [element] is a JSON string exactly where [descriptor] has a string or enum; null passes. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun checkTypes(element: JsonElement, descriptor: SerialDescriptor) {
        if (element is JsonNull) return // whether null is allowed is the decoder's check
        when (descriptor.kind) {
            PrimitiveKind.STRING, SerialKind.ENUM -> ensure((element as? JsonPrimitive)?.isString == true)

            is PrimitiveKind -> ensure(element is JsonPrimitive && !element.isString)

            StructureKind.CLASS -> (element as? JsonObject)?.let { obj ->
                for (i in 0 until descriptor.elementsCount) {
                    obj[descriptor.getElementName(i)]?.let { checkTypes(it, descriptor.getElementDescriptor(i)) }
                }
            }

            StructureKind.LIST -> (element as? JsonArray)?.forEach {
                checkTypes(it, descriptor.getElementDescriptor(0))
            }

            StructureKind.MAP -> (element as? JsonObject)?.values?.forEach {
                checkTypes(it, descriptor.getElementDescriptor(1))
            }

            else -> Unit // the decoder refuses a wrong structure
        }
    }
}

/** The manifest's own rules (05 §8): its format id, its versions (D-130 Q3), its checksums. */
internal object ManifestRules {
    private const val FORMAT = "format"
    private const val FORMAT_VERSION = "formatVersion"
    private const val SCHEMA_VERSION = "schemaVersion"
    private const val SETTINGS_SCHEMA_VERSION = "settingsSchemaVersion"
    private val DIGITS = Regex("""\d{1,30}""")
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    /** `format` is the string `umamit-backup`. */
    fun isBackup(tree: JsonObject): Boolean =
        (tree[FORMAT] as? JsonPrimitive)?.takeIf { it.isString }?.content == BackupFormat.FORMAT_ID

    /** The versions, read before the strict decode, since a newer manifest may have other keys. */
    fun checkVersions(tree: JsonObject) {
        version(tree, FORMAT_VERSION, BackupFormat.FORMAT_VERSION) // 1 is the only one so far
        version(tree, SCHEMA_VERSION, UmamiDatabase.VERSION)
        version(tree, SETTINGS_SCHEMA_VERSION, UserSettings.CURRENT_SCHEMA_VERSION)
    }

    /** [key] a whole number from 1 to [current]; newer is [BackupRefusal.NEWER_VERSION], anything else damage. */
    private fun version(tree: JsonObject, key: String, current: Int) {
        val version = (tree[key] as? JsonPrimitive)
            ?.takeIf { !it.isString && DIGITS.matches(it.content) }
            ?.content?.toBigInteger()
            ?: refuse(BackupRefusal.DAMAGED)
        if (version > BigInteger.valueOf(current.toLong())) refuse(BackupRefusal.NEWER_VERSION)
        ensure(version >= BigInteger.ONE)
    }

    /** The checksums name exactly the other entries, each a lowercase hex SHA-256; `createdAt` and the zone read. */
    fun check(manifest: BackupManifest, names: Set<String>) {
        ensure(manifest.checksums.keys == names - BackupFormat.MANIFEST)
        ensure(manifest.checksums.values.all { SHA256_HEX.matches(it) })
        ensure(BackupFormat.DATA in names && BackupFormat.SETTINGS in names)
        ensure(manifest.createdAt >= 0)
        ensure(validZone(manifest.zoneId))
    }
}

/** [block] on [value], with every decoding failure (types, enums, missing or unknown keys) as damage. */
internal inline fun <T, R> decode(value: T, block: (T) -> R): R = try {
    block(value)
} catch (_: SerializationException) {
    refuse(BackupRefusal.DAMAGED)
} catch (_: IllegalArgumentException) {
    refuse(BackupRefusal.DAMAGED) // NumberFormatException and kotlinx's own argument errors
}

/** An IANA zone id the app can use. */
internal fun validZone(id: String): Boolean = try {
    ZoneId.of(id)
    true
} catch (_: DateTimeException) {
    false
}
