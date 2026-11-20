package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The nine seed vessels of the [SeedDataSeeder] test fixture (WARE-06): all nine with their fixed IDs on an empty
 * database, none on one that holds data. The app seeds nothing since D-135. On in-memory Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeedVesselsTest {
    private val data = TestDatabase()
    private val settings = FakeSettingsRepository()
    private val seeder get() = SeedDataSeeder(
        data.db,
        data.teas,
        data.recipes,
        data.vessels,
        data.stock,
        settings,
        data.time,
    )

    @After
    fun tearDown() = data.close()

    @Test
    fun aFreshInstallHasAllNineVessels() = runTest {
        seeder.seedIfNeeded()

        assertThat(vessels().map { it.copy(id = "") }).containsExactly(
            vessel("Becher 250 ml", VesselType.MUG_INFUSER, capacityMl = 300, usableMl = 250),
            vessel("Becher 300 ml", VesselType.MUG_INFUSER, capacityMl = 350, usableMl = 300),
            vessel("Becher 350 ml", VesselType.MUG_INFUSER, capacityMl = 400, usableMl = 350),
            vessel("Becher mit Sieb 400 ml", VesselType.MUG_INFUSER, capacityMl = 400, usableMl = 400),
            vessel("Kyusu 120 ml", VesselType.KYUSU, capacityMl = 150, usableMl = 120),
            vessel("Kyusu 180 ml", VesselType.KYUSU, capacityMl = 220, usableMl = 180),
            vessel("Kyusu 250 ml", VesselType.KYUSU, capacityMl = 300, usableMl = 250),
            vessel("Kyusu 300 ml", VesselType.KYUSU, capacityMl = 350, usableMl = 300),
            vessel("Kyusu 360 ml", VesselType.KYUSU, capacityMl = 420, usableMl = 360),
        ).inOrder()
        assertThat(settings.seedVesselsV2Added.first()).isTrue()
    }

    @Test
    fun aFreshInstallKeepsTheM1VesselIdsAndGivesEachVesselItsOwnFixedId() = runTest {
        seeder.seedIfNeeded()

        val ids = vessels().associate { it.name to it.id }
        assertThat(ids["Kyusu 250 ml"]).isEqualTo(SeedData.KYUSU_VESSEL_ID)
        assertThat(ids["Becher mit Sieb 400 ml"]).isEqualTo(SeedData.MUG_VESSEL_ID)
        assertThat(ids.values.toSet()).hasSize(9)
        assertThat(ids.values.toSet()).containsExactlyElementsIn(SeedData.vessels.map { it.id })
    }

    @Test
    fun aRestoredDatabaseWithoutTheMarkersGetsNoVessels() = runTest {
        // A device restore brings the database back (D-075): its data is left as it is.
        data.teas.saveTea(tea("own", name = "Gyokuro Asahi"))

        seeder.seedIfNeeded()
        seeder.seedIfNeeded()

        assertThat(vessels()).isEmpty()
        assertThat(settings.seedVesselsV2Added.first()).isTrue()
    }

    private suspend fun vessels(): List<Vessel> = data.vessels.observeVessels().first()

    private fun vessel(name: String, type: VesselType, capacityMl: Int, usableMl: Int) =
        Vessel(id = "", name = name, type = type, capacityMl = capacityMl, usableMl = usableMl)
}
