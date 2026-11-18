package com.umamit.core.backup.restore

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.backup.archive.BackupContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The replace's crash safety (05 §8, D-130 item 26, the D-130 build note of #336): a failure at each step before the
 * commit leaves the old data intact; a crash at each phase, then a new start (the gate before Room opens), ends in the
 * complete old or the complete new data, never a mix, for the import and for its undo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportCrashTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val beforeCommit = setOf(
        ImportPoint.SAFETY_COPY_WRITTEN,
        ImportPoint.PHOTOS_STAGED,
        ImportPoint.PREPARED,
        ImportPoint.IN_TRANSACTION,
    )

    /** Runs [block] with a fresh world for each point, in its own folder. */
    private fun forEachPoint(points: Collection<ImportPoint>, block: (ImportPoint, ImportHarness) -> Unit) {
        points.forEach { point ->
            val h = ImportHarness(File(folder.root, point.name)).apply { seedOld() }
            try {
                block(point, h)
            } finally {
                h.close()
            }
        }
    }

    @Test
    fun `a failure at each step before the commit leaves the old data intact`() = forEachPoint(beforeCommit) {
            point,
            h,
        ->
        val before = h.world()
        h.replacer.checkpoints = ImportCheckpoints { if (it == point) throw InjectedFailure() }

        val result = runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }

        val expected = if (point == ImportPoint.SAFETY_COPY_WRITTEN) {
            RestoreRefusal.SAFETY_COPY_FAILED
        } else {
            RestoreRefusal.REPLACE_FAILED
        }
        assertWithMessage("$point").that(result).isEqualTo(RestoreResult.Refused(expected))
        assertWithMessage("$point").that(h.world()).isEqualTo(before)
        assertWithMessage("$point").that(h.leftovers()).isEmpty()
        assertWithMessage("$point").that(h.paths.safetyCopy.exists()).isFalse()
        assertWithMessage("$point").that(h.stored().safetyCopyAt).isNull()
    }

    @Test
    fun `a crash at each phase of an import ends in the complete old or the complete new data`() =
        forEachPoint(ImportPoint.entries) { point, h ->
            val before = h.world()
            h.replacer.checkpoints = ImportCheckpoints { if (it == point) throw SimulatedCrash() }

            assertThrows(SimulatedCrash::class.java) {
                runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }
            }
            h.restart()

            if (point in beforeCommit) {
                assertWithMessage("$point: old").that(h.world()).isEqualTo(before)
                assertWithMessage("$point").that(h.paths.safetyCopy.exists()).isFalse()
                assertWithMessage("$point").that(h.stored().safetyCopyAt).isNull()
            } else {
                assertWithMessage("$point: new").that(h.world()).isEqualTo(h.newWorld())
                assertWithMessage("$point").that(h.paths.safetyCopy.exists()).isTrue()
                assertWithMessage("$point").that(h.stored().safetyCopyAt).isEqualTo(h.clock.wall)
                assertWithMessage("$point").that(runBlocking { h.undo.preview() }).isNotNull()
            }
            assertWithMessage("$point").that(h.leftovers()).isEmpty()
            assertWithMessage("$point").that(h.stored().onboardingCompleted).isTrue()
            assertWithMessage("$point").that(h.installMarker.exists()).isTrue()
        }

    @Test
    fun `a crash at each phase of an undo ends in the complete imported or the complete earlier data`() =
        forEachPoint(ImportPoint.entries - ImportPoint.SAFETY_COPY_WRITTEN - ImportPoint.SAFETY_COPY_PROMOTED) { p, h ->
            val before = h.world()
            runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }
            val importedAt = h.clock.wall
            h.clock.advanceBy(60_000)
            h.replacer.checkpoints = ImportCheckpoints { if (it == p) throw SimulatedCrash() }

            assertThrows(SimulatedCrash::class.java) { runBlocking { h.undo.undo(NO_TIMER) } }
            h.restart()

            if (p in beforeCommit) {
                assertWithMessage("$p: imported").that(h.world()).isEqualTo(h.newWorld())
                assertWithMessage("$p").that(h.paths.safetyCopy.exists()).isTrue()
                assertWithMessage("$p").that(h.stored().safetyCopyAt).isEqualTo(importedAt)
            } else {
                assertWithMessage("$p: earlier").that(h.world()).isEqualTo(before)
                assertWithMessage("$p").that(h.paths.safetyCopy.exists()).isFalse()
                assertWithMessage("$p").that(h.stored().safetyCopyAt).isNull()
            }
            assertWithMessage("$p").that(h.leftovers()).isEmpty()
        }

    @Test
    fun `a crash of a second import keeps the first copy until the second commits`() =
        forEachPoint(listOf(ImportPoint.IN_TRANSACTION, ImportPoint.COMMITTED)) { point, h ->
            runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }
            val first = h.paths.safetyCopy.readBytes().toHex()
            val firstAt = h.clock.wall
            h.clock.advanceBy(60_000)
            h.replacer.checkpoints = ImportCheckpoints { if (it == point) throw SimulatedCrash() }

            assertThrows(SimulatedCrash::class.java) {
                runBlocking { h.newContent(newRows("Zweit")).use { h.restore.restore(it, NO_TIMER) } }
            }
            h.restart()

            if (point == ImportPoint.IN_TRANSACTION) {
                assertThat(h.world()).isEqualTo(h.newWorld())
                assertThat(h.paths.safetyCopy.readBytes().toHex()).isEqualTo(first)
                assertThat(h.stored().safetyCopyAt).isEqualTo(firstAt)
            } else {
                assertThat(h.world()).isEqualTo(h.newWorld(newRows("Zweit")))
                assertThat(h.paths.safetyCopy.readBytes().toHex()).isNotEqualTo(first)
                assertThat(h.stored().safetyCopyAt).isEqualTo(h.clock.wall)
            }
            assertThat(h.leftovers()).isEmpty()
        }

    @Test
    fun `the start gate rolls back a marker whose token the database does not hold`() {
        val h = ImportHarness(File(folder.root, "gate")).apply { seedOld() }
        try {
            val before = h.world()
            // A marker as a crash right after PREPARED leaves it, with photos.new/ and a settings file.
            h.paths.photosNew.mkdirs()
            File(h.paths.photosNew, "stray.jpg").writeBytes(byteArrayOf(1, 2, 3))
            h.paths.importDir.mkdirs()
            h.paths.settings.writeText("{}")
            h.paths.safetyDir.mkdirs()
            h.paths.safetyNew.writeBytes(byteArrayOf(4))
            MarkerFile.write(
                h.paths,
                ImportMarker(kind = ImportKind.IMPORT, token = 4242, phase = ImportPhase.PREPARED),
            )

            h.restart()

            assertThat(h.world()).isEqualTo(before)
            assertThat(h.leftovers()).isEmpty()
        } finally {
            h.close()
        }
    }

    @Test
    fun `a foreign key that fails at the commit rolls everything back`() {
        // The foreign keys are deferred to the commit (tea and photo point at each other); a row whose tea is missing
        // fails there, and the transaction, the photos and the files go back (the reader never lets such a file in).
        val h = ImportHarness(File(folder.root, "foreign-key")).apply { seedOld() }
        try {
            val before = h.world()
            val valid = h.newContent()
            val broken = valid.data.photos.first().copy(id = "photo-orphan", teaId = "no-such-tea")
            val content = BackupContent(
                valid.manifest,
                valid.data.copy(photos = valid.data.photos + broken),
                valid.settings,
                valid.photosDir,
                h.paths.staging,
            )

            val result = runBlocking { content.use { h.restore.restore(it, NO_TIMER) } }

            assertThat(result).isEqualTo(RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED))
            assertThat(h.world()).isEqualTo(before)
            assertThat(h.paths.safetyCopy.exists()).isFalse()
            assertThat(h.leftovers()).isEmpty()
        } finally {
            h.close()
        }
    }
}
