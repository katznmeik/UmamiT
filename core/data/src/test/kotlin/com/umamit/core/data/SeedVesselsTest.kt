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
 * The seed vessels of v0.2.1 (WARE-06, D-093 point 5 and Q18): a fresh install gets all nine;
 * an existing install gets the seven new ones once, by their fixed IDs, and the two M1 vessels
 * get their new names only while they still carry the M1 seed names. On in-memory Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeedVesselsTest {
    private val data = TestDatabase()
    private var settings = FakeSettingsRepository()
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
    fun anExistingInstallGetsTheSevenNewVesselsAndTheM1VesselsTheirNewNames() = runTest {
        existingM1Install()

        seeder.seedIfNeeded()

        assertThat(vessels().map { it.name }).containsExactly(
            "Becher 250 ml",
            "Becher 300 ml",
            "Becher 350 ml",
            "Becher mit Sieb 400 ml",
            "Kyusu 120 ml",
            "Kyusu 180 ml",
            "Kyusu 250 ml",
            "Kyusu 300 ml",
            "Kyusu 360 ml",
        ).inOrder()
        assertThat(vessels()).containsExactlyElementsIn(SeedData.vessels)
        assertThat(settings.seedVesselsV2Added.first()).isTrue()
        // The tea of the existing install is untouched: no second base seed.
        assertThat(data.teas.observeTeas().first().map { it.tea.id }).containsExactly("own")
    }

    @Test
    fun theNewVesselsAreInsertedOnceAndNeverTwice() = runTest {
        existingM1Install()
        seeder.seedIfNeeded()
        val afterFirst = vessels()

        seeder.seedIfNeeded()
        // Even with the marker lost (a crash before it was written), the IDs keep them single.
        settings = FakeSettingsRepository(seeded = true)
        seeder.seedIfNeeded()

        assertThat(vessels()).isEqualTo(afterFirst)
        assertThat(vessels()).hasSize(9)
    }

    @Test
    fun aNewVesselThatAlreadyExistsIsNotOverwritten() = runTest {
        existingM1Install()
        val mine = Vessel(SeedData.KYUSU_120_VESSEL_ID, "Mein Shiboridashi", VesselType.SHIBORIDASHI, capacityMl = 140)
        data.vessels.saveVessel(mine)

        seeder.seedIfNeeded()

        assertThat(data.vessels.getVessel(mine.id)).isEqualTo(mine)
        assertThat(vessels()).hasSize(9)
    }

    @Test
    fun aVesselThePoRenamedIsNotTouched() = runTest {
        existingM1Install(kyusuName = "Banko-Kyusu")

        seeder.seedIfNeeded()

        assertThat(data.vessels.getVessel(SeedData.KYUSU_VESSEL_ID)!!.name).isEqualTo("Banko-Kyusu")
        // The other M1 vessel still had its seed name, so it gets the new one.
        assertThat(data.vessels.getVessel(SeedData.MUG_VESSEL_ID)!!.name).isEqualTo("Becher mit Sieb 400 ml")
    }

    @Test
    fun theRenameChangesOnlyTheName() = runTest {
        existingM1Install()
        val kyusu = data.vessels.getVessel(SeedData.KYUSU_VESSEL_ID)!!.copy(isFavorite = true, notes = "Tokoname")
        data.vessels.saveVessel(kyusu)

        seeder.seedIfNeeded()

        assertThat(data.vessels.getVessel(SeedData.KYUSU_VESSEL_ID)).isEqualTo(kyusu.copy(name = "Kyusu 250 ml"))
    }

    @Test
    fun anExistingInstallWithoutTheM1VesselsGetsOnlyTheSeven() = runTest {
        settings = FakeSettingsRepository(seeded = true)

        seeder.seedIfNeeded()

        assertThat(vessels().map { it.name }).containsExactly(
            "Becher 250 ml",
            "Becher 300 ml",
            "Becher 350 ml",
            "Kyusu 120 ml",
            "Kyusu 180 ml",
            "Kyusu 300 ml",
            "Kyusu 360 ml",
        ).inOrder()
    }

    @Test
    fun nothingIsAddedOrRenamedOnceTheMarkerIsSet() = runTest {
        existingM1Install()
        settings = FakeSettingsRepository(seeded = true, seedVesselsV2Added = true)

        seeder.seedIfNeeded()

        assertThat(vessels().map { it.name }).containsExactly("Becher mit Sieb", "Kyusu").inOrder()
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

    /** A v0.2.0 install: seeded, its M1 vessels under their M1 names, and a tea of its own. */
    private suspend fun existingM1Install(kyusuName: String = "Kyusu") {
        settings = FakeSettingsRepository(seeded = true)
        data.vessels.saveVessel(
            Vessel(SeedData.KYUSU_VESSEL_ID, kyusuName, VesselType.KYUSU, capacityMl = 300, usableMl = 250),
        )
        data.vessels.saveVessel(
            Vessel(SeedData.MUG_VESSEL_ID, "Becher mit Sieb", VesselType.MUG_INFUSER, capacityMl = 400, usableMl = 400),
        )
        data.teas.saveTea(tea("own", name = "Gyokuro Asahi"))
    }

    private suspend fun vessels(): List<Vessel> = data.vessels.observeVessels().first()

    private fun vessel(name: String, type: VesselType, capacityMl: Int, usableMl: Int) =
        Vessel(id = "", name = name, type = type, capacityMl = capacityMl, usableMl = usableMl)
}
