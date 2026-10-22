package com.umamit.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import java.time.ZoneId
import java.time.ZoneOffset

/** An in-memory database with all collection repositories on top of it. */
class TestDatabase(now: Long = 1_000L) {
    val db: UmamiDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val time = object : WallClock {
        override fun nowMs(): Long = now

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    val teas = RoomTeaRepository(db.teaDao(), time)
    val recipes = RoomRecipeRepository(db.recipeDao(), time)
    val vessels = RoomVesselRepository(db.vesselDao(), time)
    val stock = RoomStockRepository(db.inventoryDao(), time)

    fun close() = db.close()
}

fun tea(id: String, name: String = "Tea $id") =
    Tea(id = id, name = name, category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF)

fun recipe(
    id: String,
    teaId: String,
    isDefault: Boolean = false,
    steps: List<RecipeStep> = listOf(RecipeStep(StepKind.INFUSION, 60)),
) = Recipe(
    id = id,
    teaId = teaId,
    name = "Rezept $id",
    method = BrewMethod.KYUSU,
    leafGrams = 4.0,
    waterMl = 100,
    baseTempC = 60,
    steps = steps,
    isDefault = isDefault,
)
