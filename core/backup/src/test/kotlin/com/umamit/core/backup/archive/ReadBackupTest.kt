package com.umamit.core.backup.archive

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.settingsDataStore
import com.umamit.core.model.ThemeMode
import com.umamit.core.testing.FakeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * The backup reader (BAK-02 AC2, 05 §8 „Validation", #335) on synthetic files built in the test: the round trip with
 * #334's writer, one file per refusal reason, the ZIP bomb and zip-slip before any large write. Every refusal leaves
 * the app's database, photos and settings byte for byte as they were, and its staging deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real JPEG and PNG encoding and bounds decoding
class ReadBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The app's own data, which no read may change: a file-backed `umamit.db`, `photos/` and the settings file. */
    private val appDir by lazy { File(folder.root, "app") }
    private val staging by lazy { File(folder.root, "nobackup/import") }
    private var highWater = 0L
    private val reader = ReadBackup(BackupLimits()) { highWater = maxOf(highWater, it) }

    private val photos: Map<String, ByteArray> by lazy {
        validData().photos.associate { it.id to jpeg(width = 64 + it.id.length, height = 48) }
    }

    @Before
    fun setUp() = runBlocking {
        val db = Room.databaseBuilder(context, UmamiDatabase::class.java, File(appDir, "umamit.db").path).build()
        insertFixture(db, File(appDir, "photos"))
        db.close()
        SettingsDataSource(settingsDataStore(storeScope) { File(appDir, "datastore/settings.json") })
            .update { it.copy(theme = ThemeMode.DARK) }
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun jpeg(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(format, 85, it) }.toByteArray()
    }

    /** Path → SHA-256 of every file under the app's directory. */
    private fun appState(): Map<String, String> = appDir.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(appDir).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).contentToString()
    }

    private fun read(bytes: ByteArray) = runBlocking { reader.read(ByteArrayInputStream(bytes), staging) }

    private fun assertRefused(bytes: ByteArray, refusal: BackupRefusal, why: String) {
        val before = appState()
        val result = read(bytes)
        assertWithMessage(why).that(result).isEqualTo(BackupReadResult.Refused(refusal))
        assertWithMessage("$why: staging deleted").that(staging.exists()).isFalse()
        assertWithMessage("$why: app data unchanged").that(appState()).isEqualTo(before)
    }

    private fun valid(entries: List<TestEntry> = backupEntries(photos)) = zip(entries)

    private fun damaged(why: String, entries: List<TestEntry>) = assertRefused(zip(entries), BackupRefusal.DAMAGED, why)

    // Round trip (#334's writer)

    @Test
    fun `a file of the writer reads back every row, setting and photo`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).build()
        val photosDir = File(folder.root, "source-photos")
        insertFixture(db, photosDir)
        val jpegs = (Ids.EXPORTED_PHOTOS + Ids.PHOTO_RUNNING).associateWith { jpeg(320, 240 + it.length) }
        jpegs.forEach { (id, bytes) -> File(photosDir, "$id.jpg").writeBytes(bytes) }
        val settings = SettingsDataSource(settingsDataStore(storeScope) { File(folder.root, "source-settings.json") })
        settings.update {
            it.copy(theme = ThemeMode.LIGHT, preAlertSeconds = 20, alertVolumePercent = 60, lowStockGrams = 40.0)
        }
        val clock = FakeClock()
        val out = ByteArrayOutputStream()
        CreateBackup(db, settings, BackupPhotoDirectory(photosDir), AppVersion("0.9.0", 90_099), clock).writeTo(out)

        val result = reader.read(ByteArrayInputStream(out.toByteArray()), staging)

        assertThat(result).isInstanceOf(BackupReadResult.Valid::class.java)
        val content = (result as BackupReadResult.Valid).content
        assertThat(content.rows).isEqualTo(db.backupDao().snapshot())
        assertThat(content.settings).isEqualTo(settingsJson(settings.stored()))
        assertThat(content.createdAt).isEqualTo(clock.wall)
        assertThat(
            content.preview,
        ).isEqualTo(BackupPreview(clock.wall, clock.zone.id, sessions = 2, teas = 2, photos = 4))
        assertThat(content.photosDir.list()!!.toSet()).isEqualTo(Ids.EXPORTED_PHOTOS.map { "$it.jpg" }.toSet())
        Ids.EXPORTED_PHOTOS.forEach { id ->
            assertWithMessage(id).that(content.photoFile(id).readBytes()).isEqualTo(jpegs.getValue(id))
        }
        assertThat(staging.list()!!.toList()).containsExactly("photos") // the copy and the JSON temps are gone
        content.close()
        assertThat(staging.exists()).isFalse()
        content.close() // idempotent
        db.close()
    }

    @Test
    fun `a file on disk is read in place and the entry order does not matter`() = runBlocking {
        val file = File(folder.root, "safety.umamit")
        file.writeBytes(zip(backupEntries(photos).reversed())) // manifest first, photos last
        val result = reader.read(file, staging)
        assertThat(result).isInstanceOf(BackupReadResult.Valid::class.java)
        val content = (result as BackupReadResult.Valid).content
        assertThat(content.data).isEqualTo(validData())
        assertThat(content.settings).isEqualTo(settingsTree())
        assertThat(file.exists()).isTrue()
        content.close()
    }

    @Test
    fun `old staging is cleared before a read`() {
        File(staging, "photos/left-over.jpg").apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1))
        val content = (read(valid()) as BackupReadResult.Valid).content
        assertThat(content.photosDir.list()!!.toSet()).isEqualTo(photos.keys.map { "$it.jpg" }.toSet())
        content.close()
    }

    // „Das ist keine UmamiT-Sicherung."

    @Test
    fun `not a zip is not a backup`() {
        assertRefused("{\"format\":\"umamit-backup\"}".encodeToByteArray(), BackupRefusal.NOT_A_BACKUP, "JSON")
        assertRefused(ByteArray(0), BackupRefusal.NOT_A_BACKUP, "empty")
        assertRefused(zip(emptyList()), BackupRefusal.NOT_A_BACKUP, "empty ZIP")
    }

    @Test
    fun `a zip without a manifest or with another format id is not a backup`() {
        val noManifest = backupEntries(photos).filter { it.name != BackupFormat.MANIFEST }
        assertRefused(zip(noManifest), BackupRefusal.NOT_A_BACKUP, "no manifest")
        val foreign = listOf(TestEntry("docs/", ByteArray(0)), TestEntry("docs/readme.txt", "hi".encodeToByteArray()))
        assertRefused(zip(foreign), BackupRefusal.NOT_A_BACKUP, "a foreign ZIP with a folder")
        val otherFormat = backupEntries(photos) { it.with("format", JsonPrimitive("umamit-recipe")) }
        assertRefused(zip(otherFormat), BackupRefusal.NOT_A_BACKUP, "another format id")
        val noFormat = backupEntries(photos) { it.with("format", null) }
        assertRefused(zip(noFormat), BackupRefusal.NOT_A_BACKUP, "no format id")
        val notJson = backupEntries(photos).map {
            if (it.name == BackupFormat.MANIFEST) TestEntry(it.name, "not json".encodeToByteArray()) else it
        }
        assertRefused(zip(notJson), BackupRefusal.NOT_A_BACKUP, "a manifest that is not JSON")
    }

    // „Diese Datei stammt aus einer neueren UmamiT-Version. …"

    @Test
    fun `a newer format or schema version is refused as newer`() {
        listOf("formatVersion" to 2, "schemaVersion" to UmamiDatabase.VERSION + 1, "settingsSchemaVersion" to 4)
            .forEach { (key, version) ->
                val entries = backupEntries(photos) { it.with(key, JsonPrimitive(version)) }
                assertRefused(zip(entries), BackupRefusal.NEWER_VERSION, key)
            }
        val huge = backupEntries(photos) { it.with("formatVersion", JsonPrimitive(99_999_999_999L)) }
        assertRefused(zip(huge), BackupRefusal.NEWER_VERSION, "a version beyond Int")
    }

    @Test
    fun `a newer file with other keys and entries is newer, not damaged`() {
        val entries = backupEntries(
            photos,
            extra = listOf(TestEntry("recipes/shared.json", "{}".encodeToByteArray())),
        ) { it.with("formatVersion", JsonPrimitive(2)).with("encryption", JsonPrimitive("none")) }
        assertRefused(zip(entries), BackupRefusal.NEWER_VERSION, "format 2 with a new entry and key")
    }

    // „Die Sicherung ist beschädigt und wurde nicht eingespielt."

    @Test
    fun `broken versions are damaged`() {
        listOf(
            "formatVersion" to JsonPrimitive(0),
            "formatVersion" to JsonPrimitive("1"),
            "formatVersion" to null,
            "schemaVersion" to JsonPrimitive(0),
            "schemaVersion" to JsonPrimitive(1.5),
            "settingsSchemaVersion" to null,
        ).forEach { (key, value) ->
            damaged("$key = $value", backupEntries(photos) { it.with(key, value) })
        }
    }

    @Test
    fun `the zip limits are refused`() {
        val tooMany = backupEntries(photos, extra = (1..5_000).map { TestEntry("photos/x$it.jpg", byteArrayOf(1)) })
        damaged("more than 5 000 entries", tooMany)
        val bigPhoto = photos + ("photo-big" to ByteArray(20_000_001))
        damaged("a photo over 20 MB", backupEntries(bigPhoto))
        val small = ReadBackup(BackupLimits(maxTotalBytes = 50_000, maxDataBytes = 20_000)) {}
        val before = appState()
        val total = runBlocking {
            small.read(ByteArrayInputStream(valid(backupEntries(photos + ("photo-pad" to ByteArray(45_000))))), staging)
        }
        assertThat(total).isEqualTo(BackupReadResult.Refused(BackupRefusal.DAMAGED))
        val tooLargeJson = runBlocking {
            val padded = dataTree().withRow("teas", 0) { it.with("description", JsonPrimitive("x".repeat(9_999))) }
                .withRow("teas", 1) { it.with("description", JsonPrimitive("y".repeat(9_999))) }
                .withRow("vessels", 0) { it.with("notes", JsonPrimitive("z".repeat(9_999))) }
            small.read(ByteArrayInputStream(zip(backupEntries(photos, data = padded))), staging)
        }
        assertThat(tooLargeJson).isEqualTo(BackupReadResult.Refused(BackupRefusal.DAMAGED))
        assertThat(staging.exists()).isFalse()
        assertThat(appState()).isEqualTo(before)
    }

    /** A backup whose [entry] (a JSON entry) has [pad] spaces after its JSON, the manifest's checksum to match. */
    private fun padded(entry: String, pad: Int): ByteArray {
        val entries = backupEntries(photos).map {
            if (it.name == entry) TestEntry(it.name, it.bytes + " ".repeat(pad).encodeToByteArray()) else it
        }
        val others = entries.filter { it.name != BackupFormat.MANIFEST }
        val manifest = BackupFormat.json.parseToJsonElement(entries.last().bytes.decodeToString()) as JsonObject
        val sums = JsonObject(others.associate { it.name to JsonPrimitive(sha256(it.bytes)) })
        return zip(others + TestEntry(BackupFormat.MANIFEST, manifest.with("checksums", sums).toBytes()))
    }

    @Test
    fun `the JSON caps are 64 MB for data json and 1 MB for the manifest and settings json`() {
        assertThat(BackupLimits().maxDataBytes).isEqualTo(64_000_000L)
        assertThat(BackupLimits().maxSmallJsonBytes).isEqualTo(1_000_000L)
        val small = ReadBackup(BackupLimits(maxSmallJsonBytes = 4_000)) {}
        fun readWith(reader: ReadBackup, bytes: ByteArray) =
            runBlocking { reader.read(ByteArrayInputStream(bytes), staging) }.also {
                (it as? BackupReadResult.Valid)?.content?.close()
            }

        // settings.json over its cap is damaged; the same bytes under the default caps read.
        val bigSettings = padded(BackupFormat.SETTINGS, 5_000)
        assertThat(readWith(small, bigSettings)).isEqualTo(BackupReadResult.Refused(BackupRefusal.DAMAGED))
        assertThat(readWith(reader, bigSettings)).isInstanceOf(BackupReadResult.Valid::class.java)
        // data.json has its own, larger cap.
        assertThat(readWith(small, padded(BackupFormat.DATA, 5_000))).isInstanceOf(BackupReadResult.Valid::class.java)
        assertThat(staging.exists()).isFalse()
    }

    @Test
    fun `running out of memory while reading is damage, never a crash`() {
        val before = appState()
        val starving = ReadBackup(BackupLimits()) { throw OutOfMemoryError("synthetic") }

        val result = runBlocking { starving.read(ByteArrayInputStream(valid()), staging) }

        assertThat(result).isEqualTo(BackupReadResult.Refused(BackupRefusal.DAMAGED))
        assertThat(staging.exists()).isFalse()
        assertThat(appState()).isEqualTo(before)
    }

    @Test
    fun `a zip bomb is refused before any large write`() {
        // 250 MiB of zeros deflate to about 250 KB; declared honestly, it is refused before anything is written.
        val bomb = backupEntries(photos).map {
            if (it.name == BackupFormat.DATA) TestEntry(it.name, ByteArray(0), zeros = 250L shl 20) else it
        }
        val bytes = zip(bomb)
        highWater = 0
        assertRefused(bytes, BackupRefusal.DAMAGED, "a 250 MiB data.json")
        assertThat(highWater).isLessThan(100_000L)

        // The same bomb with its declared size forged small: the ratio stops it after at most 100 × its compressed
        // size, far below its 250 MiB.
        highWater = 0
        assertRefused(forgeSize(bytes, BackupFormat.DATA, size = 1_000), BackupRefusal.DAMAGED, "a forged size")
        assertThat(highWater).isLessThan(30_000_000L)

        // Its compressed size forged up to the file's size, to raise the ratio cap: the compressed sizes no longer
        // fit in the file, refused from the central directory.
        highWater = 0
        val forgedUp = forgeSize(bytes, BackupFormat.DATA, size = 1_000, offset = COMPRESSED_SIZE, value = bytes.size)
        assertRefused(forgedUp, BackupRefusal.DAMAGED, "a forged compressed size")
        assertThat(highWater).isEqualTo(0L)
    }

    @Test
    fun `a compression ratio over 100 is refused`() {
        // A photo row's entry deflated from 2 MB of zeros (about 2 KB): under every size limit, far over 100 : 1.
        val sparse = backupEntries(photos).map {
            if (it.name == BackupFormat.photoEntry(Ids.PHOTO_OWN)) TestEntry(it.name, ByteArray(2_000_000)) else it
        }
        highWater = 0
        damaged("ratio", fixChecksums(sparse))
        assertThat(highWater).isLessThan(500_000L)
    }

    @Test
    fun `zip slip, absolute and duplicate paths are refused before anything is written`() {
        listOf(
            "../evil.jpg",
            "photos/../../evil.jpg",
            "/data/data/com.umamit.app/files/photos/x.jpg",
            "photos\\x.jpg",
            "C:/x.jpg",
            "photos//x.jpg",
            "photos/./x.jpg",
        ).forEach { name ->
            highWater = 0
            damaged(name, backupEntries(photos, extra = listOf(TestEntry(name, byteArrayOf(1)))))
            assertWithMessage("$name: nothing written").that(highWater).isEqualTo(0L)
            assertThat(File(folder.root, "nobackup/evil.jpg").exists()).isFalse()
            assertThat(File(folder.root, "evil.jpg").exists()).isFalse()
        }
        val duplicate = forgeName(
            zip(backupEntries(photos, extra = listOf(TestEntry("data.jsoX", "{}".encodeToByteArray())))),
            from = "data.jsoX",
            to = BackupFormat.DATA,
        )
        highWater = 0
        assertRefused(duplicate, BackupRefusal.DAMAGED, "a duplicate data.json")
        assertThat(highWater).isEqualTo(0L)
    }

    @Test
    fun `an unknown entry, a photo id that is not a file name and a truncated zip are damaged`() {
        damaged("unknown entry", backupEntries(photos, extra = listOf(TestEntry("notes.txt", byteArrayOf(1)))))
        damaged("a folder entry", backupEntries(photos, extra = listOf(TestEntry("photos/", ByteArray(0)))))
        damaged(
            "photo id with a dot",
            backupEntries(photos, extra = listOf(TestEntry("photos/a.b.jpg", byteArrayOf(1)))),
        )
        val bytes = valid()
        assertRefused(bytes.copyOf(bytes.size - 30), BackupRefusal.DAMAGED, "truncated")
        // A reserved deflate block type as data.json's first compressed byte: the inflater fails while reading.
        val broken = bytes.copyOf()
        broken[localData(broken, BackupFormat.DATA)] = 0xFF.toByte()
        assertRefused(broken, BackupRefusal.DAMAGED, "a broken deflate stream")
        assertRefused(ZIP_MAGIC + ByteArray(100), BackupRefusal.DAMAGED, "a signature and garbage")
    }

    @Test
    fun `a checksum mismatch or a checksum list that is not the entries is damaged`() {
        val swapped = backupEntries(photos).map {
            if (it.name ==
                BackupFormat.photoEntry(Ids.PHOTO_OWN)
            ) {
                TestEntry(it.name, jpeg(65, 49), stored = true)
            } else {
                it
            }
        }
        damaged("a photo changed after the manifest", swapped)
        val data = backupEntries(photos) { manifest ->
            val sums = manifest.getValue("checksums") as JsonObject
            manifest.with("checksums", sums.with(BackupFormat.DATA, JsonPrimitive("0".repeat(64))))
        }
        damaged("data.json's checksum", data)
        val missing = backupEntries(photos) { manifest ->
            manifest.with("checksums", (manifest.getValue("checksums") as JsonObject).with(BackupFormat.SETTINGS, null))
        }
        damaged("a missing checksum", missing)
        val extra = backupEntries(photos) { manifest ->
            val sums = manifest.getValue("checksums") as JsonObject
            manifest.with("checksums", sums.with("photos/ghost.jpg", JsonPrimitive("0".repeat(64))))
        }
        damaged("a checksum without its entry", extra)
        val upper = backupEntries(photos) { manifest ->
            val sums = manifest.getValue("checksums") as JsonObject
            manifest.with(
                "checksums",
                JsonObject(
                    sums.mapValues {
                        JsonPrimitive(it.value.toString().trim('"').uppercase())
                    },
                ),
            )
        }
        damaged("uppercase hex", upper)
        damaged("no settings.json", backupEntries(photos).filter { it.name != BackupFormat.SETTINGS })
        damaged("an unknown manifest key", backupEntries(photos) { it.with("extra", JsonPrimitive(1)) })
        damaged("a bad zone", backupEntries(photos) { it.with("zoneId", JsonPrimitive("Mars/Olympus")) })
        damaged(
            "counts that are not the rows",
            backupEntries(photos) { manifest ->
                manifest.with("counts", (manifest.getValue("counts") as JsonObject).with("teas", JsonPrimitive(3)))
            },
        )
    }

    @Test
    fun `a type, enum or constraint violation is damaged`() {
        val cases = mapOf(
            "a string for a number" to dataTree().withRow("brewProfiles", 0) { it.with("waterMl", JsonPrimitive("x")) },
            "a number for a string" to dataTree().withRow("teas", 0) { it.with("name", JsonPrimitive(7)) },
            "an unknown enum name" to dataTree().withRow("teas", 0) { it.with("category", JsonPrimitive("PURPLE")) },
            "an unknown key" to dataTree().withRow("vessels", 0) { it.with("colour", JsonPrimitive("blau")) },
            "a missing key" to dataTree().withRow("sessionSteps", 0) { it.with("tasteSpice", null) },
            "a missing table" to dataTree().with("catalogBookmarks", null),
            "a row that is not an object" to dataTree().with("teas", JsonArray(listOf(JsonPrimitive(1)))),
            "62 °C" to dataTree().withRow("brewProfiles", 0) { it.with("baseTempC", JsonPrimitive(62)) },
            "an IN_PROGRESS session" to
                dataTree().withRow("sessions", 0) { it.with("status", JsonPrimitive("IN_PROGRESS")) },
        )
        cases.forEach { (why, data) -> damaged(why, backupEntries(photos, data = data)) }
        val notAnObject = backupEntries(photos).map {
            if (it.name == BackupFormat.DATA) TestEntry(it.name, "[]".encodeToByteArray()) else it
        }
        damaged("data.json not an object", fixChecksums(notAnObject))
        val deep = backupEntries(photos).map {
            if (it.name == BackupFormat.DATA) TestEntry(it.name, ("[".repeat(100_000)).encodeToByteArray()) else it
        }
        damaged("nested too deep", fixChecksums(deep))
        val notUtf8 = backupEntries(photos).map {
            if (it.name == BackupFormat.SETTINGS) TestEntry(it.name, byteArrayOf(0x7B, 0xC3.toByte(), 0x7D)) else it
        }
        damaged("settings.json not UTF-8", fixChecksums(notUtf8))
        val deviceBound = settingsTree().with("onboardingCompleted", JsonPrimitive(true))
        damaged("a device-bound setting", backupEntries(photos, settings = deviceBound))
    }

    @Test
    fun `a broken reference is damaged`() {
        damaged(
            "a recipe of a missing tea",
            backupEntries(
                photos,
                data = dataTree().withRow("brewProfiles", 0) {
                    it.with("teaId", JsonPrimitive("x"))
                },
            ),
        )
        damaged("a photo row without its file", backupEntries(photos - Ids.PHOTO_VESSEL))
        damaged("a photo file without its row", backupEntries(photos + ("photo-extra" to jpeg(10, 10))))
    }

    @Test
    fun `a photo that is not a JPEG of at most 2048 px a side is damaged`() {
        damaged("garbage", backupEntries(photos + (Ids.PHOTO_OWN to ByteArray(500) { it.toByte() })))
        damaged("a PNG", backupEntries(photos + (Ids.PHOTO_OWN to jpeg(64, 48, Bitmap.CompressFormat.PNG))))
        damaged("2049 px wide", backupEntries(photos + (Ids.PHOTO_OWN to jpeg(2_049, 8))))
        val edge = (read(valid(backupEntries(photos + (Ids.PHOTO_OWN to jpeg(2_048, 8))))) as BackupReadResult.Valid)
        edge.content.close()
    }

    @Test
    fun `an older schema version is converted to the current one`() {
        // A schema-6 file: no step taste columns, the session holds its six tastes, no v7–v9 columns.
        var v6 = dataTree()
        listOf("teas" to listOf("tasteSet")).forEach { (table, keys) ->
            (0 until (v6.getValue(table) as JsonArray).size).forEach { i ->
                v6 = v6.withRow(table, i) { row -> JsonObject(row - keys.toSet()) }
            }
        }
        val stepKeys = setOf(
            "tasteUmami", "tasteSweetness", "tasteBitterness", "tasteAstringency", "tasteBody", "tasteAroma",
            "tastingNote", "tasteRoast", "tasteAcidity", "tasteFreshness", "tasteSpice",
        )
        (0 until 3).forEach { i -> v6 = v6.withRow("sessionSteps", i) { JsonObject(it - stepKeys) } }
        (0 until 2).forEach { i -> v6 = v6.withRow("sessions", i) { JsonObject(it - "brewingRating") } }
        val content = (read(zip(backupEntries(photos, data = v6, schemaVersion = 6))) as BackupReadResult.Valid).content
        val session = content.data.sessions.first { it.id == Ids.COMPLETED }
        assertThat(session.tasteUmami).isNull()
        assertThat(session.brewingRating).isEqualTo(session.rating)
        val first = content.data.sessionSteps.first { it.id == "ss-1" }
        assertThat(first.tasteUmami).isEqualTo(5)
        assertThat(first.tasteRoast).isNull()
        assertThat(content.data.sessionSteps.first { it.id == "ss-2" }.tasteUmami).isNull()
        assertThat(content.data.teas.all { it.tasteSet == null }).isTrue()
        content.close()
    }

    /** [entries] with the manifest's checksums recomputed, for a file whose damage is behind the checksums. */
    private fun fixChecksums(entries: List<TestEntry>): List<TestEntry> {
        val others = entries.filter { it.name != BackupFormat.MANIFEST }
        val manifest = entries.first { it.name == BackupFormat.MANIFEST }
        val tree = BackupFormat.json.parseToJsonElement(manifest.bytes.decodeToString()) as JsonObject
        val fixed = tree.with("checksums", JsonObject(others.associate { it.name to JsonPrimitive(sha256(it.bytes)) }))
        return others + TestEntry(BackupFormat.MANIFEST, fixed.toBytes())
    }

    /** [bytes] with the central directory's uncompressed size of entry [name] set to [size]. */
    private fun forgeSize(bytes: ByteArray, name: String, size: Int, offset: Int? = null, value: Int = 0): ByteArray {
        val out = bytes.copyOf()
        val at = centralHeader(out, name)
        writeInt(out, at + UNCOMPRESSED_SIZE, size)
        if (offset != null) writeInt(out, at + offset, value)
        return out
    }

    private companion object {
        /** Offsets in a central directory header. */
        const val COMPRESSED_SIZE = 20
        const val UNCOMPRESSED_SIZE = 24
    }

    /** [bytes] with every occurrence of entry name [from] (local and central headers) renamed to [to]. */
    private fun forgeName(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val out = bytes.copyOf()
        val needle = from.encodeToByteArray()
        var index = 0
        while (index <= out.size - needle.size) {
            if (out.copyOfRange(index, index + needle.size).contentEquals(needle)) {
                to.encodeToByteArray().copyInto(out, index)
            }
            index++
        }
        return out
    }

    private fun centralHeader(bytes: ByteArray, name: String): Int {
        val needle = name.encodeToByteArray()
        for (i in 0 until bytes.size - 46) {
            val signature = bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x01.toByte() && bytes[i + 3] == 0x02.toByte()
            if (signature && bytes.copyOfRange(i + 46, i + 46 + needle.size).contentEquals(needle)) return i
        }
        error("no central header for $name")
    }

    /** The offset of the first data byte of entry [name], after its local file header. */
    private fun localData(bytes: ByteArray, name: String): Int {
        val needle = name.encodeToByteArray()
        for (i in 0 until bytes.size - 30) {
            val signature = bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() && bytes[i + 3] == 0x04.toByte()
            if (signature && bytes.copyOfRange(i + 30, i + 30 + needle.size).contentEquals(needle)) {
                val extra = (bytes[i + 28].toInt() and 0xFF) or ((bytes[i + 29].toInt() and 0xFF) shl 8)
                return i + 30 + needle.size + extra
            }
        }
        error("no local header for $name")
    }

    private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
        (0 until 4).forEach { bytes[at + it] = (value shr (8 * it)).toByte() }
    }
}
