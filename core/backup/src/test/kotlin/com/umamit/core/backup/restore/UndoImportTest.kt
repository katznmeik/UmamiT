package com.umamit.core.backup.restore

import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.Ids
import com.umamit.core.backup.archive.session
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * *Import rückgängig machen* (BAK-02, 05 §8 „Undo", „Expiry", D-130 Q5, Q6, item 21): the data before comes back and
 * the copy goes; the dialog's counts; 7 × 24 h on the injected clock; refused while a timer is active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndoImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val h by lazy { ImportHarness(folder.root).apply { seedOld() } }
    private lateinit var before: World
    private var importedAt = 0L

    @After
    fun tearDown() = h.close()

    private fun imported() {
        before = h.world()
        val result = runBlocking { h.newContent().use { h.restore.restore(it, NO_TIMER) } }
        assertThat(result).isInstanceOf(RestoreResult.Done::class.java)
        importedAt = h.clock.wall
    }

    private fun undo(timer: TimerActivity = NO_TIMER) = runBlocking { h.undo.undo(timer) }

    private fun preview() = runBlocking { h.undo.preview() }

    @Test
    fun `undo brings the data before back and deletes the copy`() {
        imported()
        h.clock.advanceBy(3_600_000)

        val result = undo()

        assertThat(result).isInstanceOf(RestoreResult.Done::class.java)
        assertThat((result as RestoreResult.Done).finishedAtNextStart).isFalse()
        assertThat(h.world()).isEqualTo(before)
        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.stored().safetyCopyAt).isNull()
        assertThat(h.stored().onboardingCompleted).isTrue()
        assertThat(h.leftovers()).isEmpty()
        assertThat(preview()).isNull()
        assertThat(undo()).isEqualTo(RestoreResult.NoSafetyCopy)
    }

    @Test
    fun `the dialog counts 3 new sessions and 1 changed tea`() {
        imported()
        h.clock.advanceBy(60_000)
        runBlocking {
            val sessions = h.db.sessionDao()
            sessions.insert(session("after-1", SessionStatus.COMPLETED, h.clock.wall))
            sessions.insert(session("after-2", SessionStatus.MANUAL, h.clock.wall + 1))
            sessions.insert(session("after-3", SessionStatus.COMPLETED, h.clock.wall + 2))
            // Neither an unfinished session nor one started before the import counts.
            sessions.insert(session("after-running", SessionStatus.IN_PROGRESS, h.clock.wall + 3))
            sessions.insert(session("before-import", SessionStatus.COMPLETED, importedAt - 1))
            val tea = h.db.teaDao().get(Ids.TEA)!!
            h.db.teaDao().upsert(tea.copy(notes = "geändert", updatedAt = h.clock.wall))
        }

        assertThat(preview()).isEqualTo(UndoPreview(3, 1, importedAt + SAFETY_COPY_VALID_MS))
    }

    @Test
    fun `with nothing new since the import both counts are 0`() {
        imported()

        assertThat(preview()).isEqualTo(UndoPreview(0, 0, importedAt + SAFETY_COPY_VALID_MS))
    }

    @Test
    fun `the copy can be undone until 7 x 24 h after the import, not at that moment`() {
        imported()
        h.clock.advanceBy(SAFETY_COPY_VALID_MS - 1)
        assertThat(preview()).isNotNull()

        h.clock.advanceBy(1)

        assertThat(preview()).isNull()
        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.stored().safetyCopyAt).isNull()
        assertThat(undo()).isEqualTo(RestoreResult.NoSafetyCopy)
        assertThat(h.world()).isEqualTo(h.newWorld())
    }

    @Test
    fun `the expiry at app start deletes a copy at or past 7 x 24 h and clears its date`() {
        imported()
        h.clock.advanceBy(SAFETY_COPY_VALID_MS)

        h.restart()

        assertThat(h.paths.safetyCopy.exists()).isFalse()
        assertThat(h.stored().safetyCopyAt).isNull()
        assertThat(h.world()).isEqualTo(h.newWorld())
    }

    @Test
    fun `the expiry deletes a copy without a date and clears a date without a copy`() {
        imported()
        runBlocking { h.settings.setSafetyCopyAt(null) }
        h.restart()
        assertThat(h.paths.safetyCopy.exists()).isFalse()

        runBlocking { h.settings.setSafetyCopyAt(h.clock.wall) }
        h.restart()
        assertThat(h.stored().safetyCopyAt).isNull()
    }

    @Test
    fun `a running brew or a quick timer refuses the undo and changes nothing`() {
        imported()
        for (slot in TimerSlot.entries) {
            h.activeBrew(slot)
            val now = h.world()
            val storedBefore = h.stored()

            assertThat(undo()).isEqualTo(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))

            assertThat(h.world()).isEqualTo(now)
            assertThat(h.stored()).isEqualTo(storedBefore)
            assertThat(h.paths.safetyCopy.exists()).isTrue()
            assertThat(h.stored().safetyCopyAt).isEqualTo(importedAt)
            runBlocking { h.db.activeBrewDao().delete(slot) }
        }
        assertThat(undo { true }).isEqualTo(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))
        assertThat(h.paths.safetyCopy.exists()).isTrue()
    }

    @Test
    fun `a running catalog refresh refuses the undo`() {
        imported()
        h.refresh.running()

        assertThat(undo()).isEqualTo(RestoreResult.Refused(RestoreRefusal.CATALOG_REFRESH))

        assertThat(h.world()).isEqualTo(h.newWorld())
        assertThat(h.paths.safetyCopy.exists()).isTrue()
    }

    @Test
    fun `a failure before the undo's commit keeps the imported data and the copy`() {
        imported()
        h.replacer.checkpoints = ImportCheckpoints { if (it == ImportPoint.IN_TRANSACTION) throw InjectedFailure() }

        assertThat(undo()).isEqualTo(RestoreResult.Refused(RestoreRefusal.REPLACE_FAILED))

        assertThat(h.world()).isEqualTo(h.newWorld())
        assertThat(h.paths.safetyCopy.exists()).isTrue()
        assertThat(h.stored().safetyCopyAt).isEqualTo(importedAt)
        assertThat(h.leftovers()).isEmpty()
    }
}
