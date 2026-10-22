package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.UmamiLevel
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Teas and recipes through the repositories (05 §3.1–§3.3, GEN-12, BRW-02). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeaAndRecipeRepositoryTest {
    private val data = TestDatabase()

    @After
    fun tearDown() = data.close()

    @Test
    fun teaIsStoredAndReadBackWithAllFields() = runTest {
        val tea = tea("t1", "Bio Sencha Tanabata").copy(
            secondaryName = "Tanabata",
            catalogKey = "TG0557",
            umamiLevel = UmamiLevel.HIGH,
            isOrganic = true,
            originCountry = "JP",
            harvestSeason = HarvestSeason.FIRST_FLUSH,
            harvestYear = 2026,
            shadingDays = 10,
            openedDate = LocalDate.of(2026, 10, 21),
            lowStockGrams = 15.5,
        )
        data.teas.saveTea(tea)

        assertThat(data.teas.getTea("t1")).isEqualTo(tea)
    }

    @Test
    fun teasAreListedByNameIgnoringCase() = runTest {
        data.teas.saveTea(tea("a", "hisui"))
        data.teas.saveTea(tea("b", "Gyokuro"))
        data.teas.saveTea(tea("c", "Tanabata"))

        val names = data.teas.observeTeas().first().map { it.tea.name }

        assertThat(names).containsExactly("Gyokuro", "hisui", "Tanabata").inOrder()
    }

    @Test
    fun recipesAreReadInPositionOrderWithStepsInOrder() = runTest {
        data.teas.saveTea(tea("t1"))
        val steps = listOf(
            RecipeStep(StepKind.RINSE, 10, 80),
            RecipeStep(StepKind.INFUSION, 90),
            RecipeStep(StepKind.INFUSION, 30, 70, note = "kurz"),
            RecipeStep(StepKind.INFUSION, 60, 75),
        )
        data.recipes.saveRecipe(recipe("r2", "t1").copy(position = 1))
        data.recipes.saveRecipe(recipe("r1", "t1", steps = steps).copy(position = 0, preAlertSeconds = 5))

        val recipes = data.recipes.observeRecipes("t1").first()

        assertThat(recipes.map { it.id }).containsExactly("r1", "r2").inOrder()
        assertThat(recipes[0].steps).isEqualTo(steps)
        assertThat(recipes[0].preAlertSeconds).isEqualTo(5)
        assertThat(data.recipes.getRecipe("r1")).isEqualTo(recipes[0])
    }

    @Test
    fun teaOverviewCarriesItsDefaultRecipeAndCoverPhoto() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))
        data.recipes.saveRecipe(recipe("r2", "t1"))
        val photo = Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.CATALOG_COPY, 800, 600, 12_345L)
        data.teas.savePhoto(photo)
        data.teas.setCoverPhoto("t1", "p1")

        val overview = data.teas.observeTea("t1").first()!!

        assertThat(overview.defaultRecipe?.id).isEqualTo("r1")
        assertThat(overview.coverPhoto).isEqualTo(photo)
        assertThat(overview.tea.coverPhotoId).isEqualTo("p1")
    }

    @Test
    fun savingADefaultRecipeMakesItTheTeasOnlyDefault() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(recipe("r1", "t1", isDefault = true))
        data.recipes.saveRecipe(recipe("r2", "t1", isDefault = true))

        val defaults = data.recipes.observeRecipes("t1").first().filter { it.isDefault }.map { it.id }

        assertThat(defaults).containsExactly("r2")
    }

    @Test
    fun replacingStepsAndPreAlertPersistsExactlyTheNewListAndValue() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(
            recipe(
                "r1",
                "t1",
                steps = List(4) { RecipeStep(StepKind.INFUSION, 30 + it) },
            ).copy(preAlertSeconds = 10),
        )
        val newSteps = listOf(RecipeStep(StepKind.INFUSION, 75, 65), RecipeStep(StepKind.INFUSION, 20))

        data.recipes.replaceStepsAndPreAlert("r1", newSteps, preAlertSeconds = 0)

        val stored = data.recipes.getRecipe("r1")!!
        assertThat(stored.steps).isEqualTo(newSteps)
        assertThat(stored.preAlertSeconds).isEqualTo(0)

        data.recipes.replaceStepsAndPreAlert("r1", newSteps, preAlertSeconds = null)
        assertThat(data.recipes.getRecipe("r1")!!.preAlertSeconds).isNull()
    }

    @Test
    fun temperaturesAreSnappedHalfUpToMultiplesOfFiveOnWrite() = runTest {
        data.teas.saveTea(tea("t1"))
        data.recipes.saveRecipe(
            recipe("r1", "t1", steps = listOf(RecipeStep(StepKind.INFUSION, 60, 52))).copy(baseTempC = 58),
        )
        assertThat(data.recipes.getRecipe("r1")!!.baseTempC).isEqualTo(60)
        assertThat(data.recipes.getRecipe("r1")!!.steps.single().tempC).isEqualTo(50)

        data.recipes.replaceStepsAndPreAlert("r1", listOf(RecipeStep(StepKind.INFUSION, 60, 53)), null)
        assertThat(data.recipes.getRecipe("r1")!!.steps.single().tempC).isEqualTo(55)
    }

    /**
     * S7 of the #27 slice review: writes are upserts, never REPLACE (05 §3). A REPLACE deletes
     * the row first, which would cascade into the recipes, steps, photos and stock and null the
     * session links of a tea, vessel or recipe that is only edited.
     */
    @Test
    fun savingAnExistingTeaVesselRecipeOrPhotoAgainKeepsEverythingThatHangsOffIt() = runTest {
        data.teas.saveTea(tea("t1"))
        data.vessels.saveVessel(Vessel("v1", "Kyusu", VesselType.KYUSU, 300))
        data.recipes.saveRecipe(recipe("r1", "t1").copy(vesselId = "v1"))
        data.teas.savePhoto(Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.OWN, 1, 1, 1L))
        data.teas.setCoverPhoto("t1", "p1")
        data.stock.addEvent(initial("e1", "t1", 100.0, 850))
        val snapshot = snapshotOf(teaId = "t1").copy(profileId = "r1", vesselId = "v1")
        TimerDriver(data) { id, steps, preAlert -> sessionStart(id, steps, preAlert, TestClocks(), snapshot) }
            .send(startEvent(infusion(60)))

        data.teas.saveTea(data.teas.getTea("t1")!!.copy(name = "Umbenannt"))
        data.vessels.saveVessel(data.vessels.getVessel("v1")!!.copy(name = "Kyusu klein"))
        data.recipes.saveRecipe(data.recipes.getRecipe("r1")!!.copy(name = "Neu"))
        data.teas.savePhoto(Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.OWN, 2, 2, 2L))

        assertThat(data.teas.getTea("t1")!!.coverPhotoId).isEqualTo("p1")
        assertThat(data.recipes.getRecipe("r1")!!.vesselId).isEqualTo("v1")
        assertThat(data.recipes.getRecipe("r1")!!.steps).isNotEmpty()
        assertThat(data.stock.observeEvents("t1").first().map { it.id }).containsExactly("e1")
        val session = data.sessions.getSession(SESSION_ID)!!.snapshot
        assertThat(listOf(session.teaId, session.profileId, session.vesselId)).containsExactly("t1", "r1", "v1")
    }

    @Test
    fun deletingATeaCascadesToItsRecipesStepsPhotosAndStock() = runTest {
        data.teas.saveTea(tea("t1"))
        data.teas.saveTea(tea("t2"))
        data.recipes.saveRecipe(recipe("r1", "t1"))
        data.recipes.saveRecipe(recipe("r2", "t2"))
        data.teas.savePhoto(Photo("p1", PhotoOwner.OfTea("t1"), PhotoOrigin.OWN, 1, 1, 1L))
        data.teas.setCoverPhoto("t1", "p1")
        data.stock.addEvent(initial("e1", "t1", 100.0, 850))
        data.stock.addEvent(initial("e2", "t2", 50.0, 900))

        data.teas.deleteTea("t1")

        assertThat(data.teas.getTea("t1")).isNull()
        assertThat(data.recipes.getRecipe("r1")).isNull()
        assertThat(data.db.recipeDao().stepCount("r1")).isEqualTo(0)
        assertThat(data.db.teaDao().photo("p1")).isNull()
        assertThat(data.stock.observeEvents("t1").first()).isEmpty()
        assertThat(data.recipes.getRecipe("r2")).isNotNull()
        assertThat(data.stock.observeEvents("t2").first()).hasSize(1)
    }
}
