package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.timer.engine.TimerEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Archivieren* and *Wiederherstellen* of a vessel (WARE-01, D-100) on the database in memory: a vessel
 * is archived, never deleted, so recipes keep it; the vessel of the active brew session can't be
 * archived until the session ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VesselArchiveTest {
    private val data = TestDatabase()
    private val archive by lazy { RoomVesselArchive(data.db, data.time) }

    @After
    fun tearDown() = data.close()

    private val kyusu = Vessel("v1", "Kyusu", VesselType.KYUSU, 300)

    @Test
    fun `archiving keeps the vessel in its recipes and Wiederherstellen brings it back unchanged`() = runTest {
        data.vessels.saveVessel(kyusu)
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r1", "t1").copy(vesselId = "v1"))

        assertThat(archive.archive("v1")).isEqualTo(ArchiveResult.Archived)

        assertThat(data.vessels.getVessel("v1")!!.isArchived).isTrue()
        assertThat(data.recipes.getRecipe("r1")!!.vesselId).isEqualTo("v1")

        archive.restore("v1")

        assertThat(data.vessels.getVessel("v1")).isEqualTo(kyusu)
    }

    @Test
    fun `a vessel that is gone is reported`() = runTest {
        assertThat(archive.archive("gone")).isEqualTo(ArchiveResult.NotFound)
    }

    @Test
    fun `the vessel of the active brew session can't be archived until the session ends`() = runTest {
        data.vessels.saveVessel(kyusu)
        data.vessels.saveVessel(Vessel("v2", "Becher", VesselType.MUG_INFUSER, 250))
        data.teas.saveTea(tea("t1"))
        assertThat(archive.observeActiveVesselId().first()).isNull()
        val snapshot = snapshotOf(teaId = "t1").copy(vesselId = "v1")
        val driver = TimerDriver(data) { id, steps, preAlert ->
            sessionStart(id, steps, preAlert, TestClocks(), snapshot)
        }
        driver.send(startEvent(infusion(60)))

        assertThat(archive.observeActiveVesselId().first()).isEqualTo("v1")
        assertThat(archive.archive("v1")).isEqualTo(ArchiveResult.InActiveSession)
        assertThat(data.vessels.getVessel("v1")!!.isArchived).isFalse()
        assertThat(archive.archive("v2")).isEqualTo(ArchiveResult.Archived)

        driver.send(TimerEvent.Start)
        driver.clocks.advanceSeconds(60)
        driver.send(TimerEvent.DeadlineReached)
        driver.send(TimerEvent.Poured)
        driver.send(TimerEvent.FinishSession)

        assertThat(archive.observeActiveVesselId().first()).isNull()
        assertThat(archive.archive("v1")).isEqualTo(ArchiveResult.Archived)
    }
}
