package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Creates the PO's seed data on the first start (M1-07, WARE-06, 08 §2.5a): the teas
 * Tanabata and Hisui with their recipes, the nine seed vessels and 100 g of stock each. On an
 * existing install it adds the seven vessels of v0.2.1 once (D-093). The first-start flow
 * (M1-15) and every later app start (D-075) call [seedIfNeeded] off the main thread.
 */
class SeedDataSeeder @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val vessels: VesselRepository,
    private val stock: StockRepository,
    private val settings: SettingsRepository,
    private val time: WallClock,
) {
    /**
     * Seeds exactly once, in one transaction. Does nothing when the seed marker is set (also
     * after a seed tea was deleted) or when the database already holds a tea, vessel or session
     * (a device restore); in both cases the marker is set afterwards. Once seeded, adds the
     * vessels of v0.2.1 once ([addVesselsV2IfNeeded]). Safe to call on every start.
     *
     * @return true when the seed data was created by this call.
     */
    suspend fun seedIfNeeded(): Boolean {
        if (settings.seedDataCreated.first()) {
            addVesselsV2IfNeeded()
            return false
        }
        val seeded = db.withTransaction {
            if (db.teaDao().hasPersonalData()) {
                false
            } else {
                SeedData.vessels.forEach { vessels.saveVessel(it) }
                SeedData.teas.forEach { teas.saveTea(it) }
                SeedData.recipes.forEach { recipes.saveRecipe(it) }
                SeedData.initialStock(time.nowMs()).forEach { stock.addEvent(it) }
                true
            }
        }
        // Written after the commit: a crash in between leaves data, which the check above sees.
        // A fresh seed has all nine vessels; a restored database is left as it is (D-075).
        settings.markSeedVesselsV2Added()
        settings.markSeedDataCreated()
        return seeded
    }

    /**
     * The first start of v0.2.1 on an existing install (WARE-06, D-093): inserts each of the
     * seven new vessels by its fixed ID only when absent, and gives the two M1 vessels their new
     * names only while they still carry the M1 seed names (Q18), in one transaction; then sets
     * the marker. Inserting by ID keeps them single even when the marker was lost.
     */
    private suspend fun addVesselsV2IfNeeded() {
        if (settings.seedVesselsV2Added.first()) return
        db.withTransaction {
            SeedData.vesselsV2.forEach { if (vessels.getVessel(it.id) == null) vessels.saveVessel(it) }
            rename(SeedData.KYUSU_VESSEL_ID, from = SeedData.KYUSU_M1_NAME)
            rename(SeedData.MUG_VESSEL_ID, from = SeedData.MUG_M1_NAME)
        }
        settings.markSeedVesselsV2Added()
    }

    private suspend fun rename(id: String, from: String) {
        val vessel = vessels.getVessel(id) ?: return
        val to = SeedData.m1Vessels.single { it.id == id }.name
        if (vessel.name == from) vessels.saveVessel(vessel.copy(name = to))
    }
}
