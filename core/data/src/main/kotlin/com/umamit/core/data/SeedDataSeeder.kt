package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.flow.first

/**
 * Writes the [SeedData] fixtures into a database, for tests only (D-135 item 8): the teas
 * Tanabata and Hisui with their recipes, the nine seed vessels and 100 g of stock each. The app
 * never runs it: since D-135 a fresh install and *Alle Daten löschen* start empty, and nothing in
 * the app's graph can build it (no `@Inject`). Seed teas already on a phone stay as ordinary data.
 */
class SeedDataSeeder(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val vessels: VesselRepository,
    private val stock: StockRepository,
    private val settings: SettingsRepository,
    private val time: WallClock,
) {
    /**
     * Seeds exactly once, in one transaction. Does nothing when the seed marker is set or when
     * the database already holds a tea, vessel or session; in both cases the marker is set
     * afterwards.
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
        settings.markSeedVesselsV2Added()
        settings.markSeedDataCreated()
        return seeded
    }
}
