package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Creates the PO's seed data on the first start (M1-07, WARE-06, 08 §2.5a): the teas
 * Tanabata and Hisui with their recipes, the vessels „Kyusu" and „Becher mit Sieb" and
 * 100 g of stock each. The first-start flow (M1-15) calls [seedIfNeeded] off the main thread.
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
     * (a device restore); in both cases the marker is set afterwards. Safe to call on every start.
     *
     * @return true when the seed data was created by this call.
     */
    suspend fun seedIfNeeded(): Boolean {
        if (settings.seedDataCreated.first()) return false
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
        settings.markSeedDataCreated()
        return seeded
    }
}
