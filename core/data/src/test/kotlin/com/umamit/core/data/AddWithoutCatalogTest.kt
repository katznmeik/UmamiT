package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * *Ohne Katalog* (TEA-01, PRF-02, INV-01, D-100) on the database in memory: the tea, its style's
 * recipes and, only with a package, the INITIAL stock event, in one transaction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AddWithoutCatalogTest {
    /** 2026-10-31 15:00 UTC. */
    private val now = LocalDate.of(2026, 10, 31).atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val data = TestDatabase(now = now)
    private val add by lazy { RoomAddWithoutCatalog(data.db, data.teas, data.recipes, data.stock, data.time) }

    @After
    fun tearDown() = data.close()

    private fun draft(styleKey: String? = "sencha") = Tea(
        id = "",
        name = "Geschenk-Sencha",
        category = TeaCategory.GREEN,
        form = TeaForm.LOOSE_LEAF,
        styleKey = styleKey,
        vendor = "Teeladen am Markt",
        // A manual tea never starts with „Rezept prüfen" (D-100).
        recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT,
    )

    @Test
    fun `a tea with a package gets its style's recipes and the INITIAL event`() = runTest {
        val result = add.add(
            draft(),
            TeaPurchase(grams = 50.0, priceCents = 1290, purchasedOn = LocalDate.of(2026, 10, 31)),
        )

        val teaId = (result as AddWithoutCatalogResult.Added).teaId
        val tea = data.teas.getTea(teaId)!!
        assertThat(tea).isEqualTo(draft().copy(id = teaId, recipeCheckReason = null))
        // Sencha's rows of 08 §3: Kyusu 5 g / 150 ml / 75 °C, the default, then the cold brew.
        val recipes = data.recipes.observeRecipes(teaId).first()
        assertThat(recipes.map { it.name }).containsExactly("Kyusu", "Kaltaufguss").inOrder()
        assertThat(recipes.map { it.isDefault }).containsExactly(true, false).inOrder()
        assertThat(recipes[0].leafGrams).isEqualTo(5.0)
        assertThat(recipes[0].waterMl).isEqualTo(150)
        assertThat(recipes[0].baseTempC).isEqualTo(75)
        assertThat(recipes.map { it.source }.toSet()).containsExactly(RecipeSource.STYLE_DEFAULT)
        val event = data.stock.observeEvents(teaId).first().single()
        assertThat(event.type).isEqualTo(InventoryEventType.INITIAL)
        assertThat(event.deltaGrams).isEqualTo(50.0)
        assertThat(event.priceCents).isEqualTo(1290)
        assertThat(event.packageLabel).isEqualTo("50 g")
        // Bought today: now.
        assertThat(event.occurredAtMillis).isEqualTo(now)
        assertThat(data.teas.observeOwnedTeas().first().map { it.tea.id }).containsExactly(teaId)
    }

    @Test
    fun `a package bought on another day is dated at noon, a missing price stays unknown`() = runTest {
        val result = add.add(
            draft(),
            TeaPurchase(grams = 0.5, priceCents = null, purchasedOn = LocalDate.of(2026, 10, 2)),
        )

        val event = data.stock.observeEvents((result as AddWithoutCatalogResult.Added).teaId).first().single()
        assertThat(event.occurredAtMillis)
            .isEqualTo(LocalDate.of(2026, 10, 2).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        assertThat(event.priceCents).isNull()
        assertThat(event.packageLabel).isEqualTo("0,5 g")
    }

    @Test
    fun `a tea without a package has no stock events`() = runTest {
        val result = add.add(draft(), purchase = null)

        val teaId = (result as AddWithoutCatalogResult.Added).teaId
        assertThat(data.stock.observeEvents(teaId).first()).isEmpty()
        assertThat(data.recipes.observeRecipes(teaId).first()).isNotEmpty()
    }

    @Test
    fun `a tea without a known style gets the Western default of PRF-02`() = runTest {
        for (style in listOf(null, "no_such_style")) {
            val result = add.add(draft(styleKey = style), purchase = null)

            val recipe = data.recipes.observeRecipes((result as AddWithoutCatalogResult.Added).teaId).first().single()
            assertThat(recipe.method).isEqualTo(BrewMethod.WESTERN)
            assertThat(recipe.leafGrams).isEqualTo(3.0)
            assertThat(recipe.waterMl).isEqualTo(250)
            assertThat(recipe.baseTempC).isEqualTo(85)
            assertThat(recipe.steps).containsExactly(RecipeStep(StepKind.INFUSION, 180))
            assertThat(recipe.isDefault).isTrue()
        }
    }

    @Test
    fun `every add is a new tea`() = runTest {
        val first = add.add(draft(), purchase = null) as AddWithoutCatalogResult.Added
        val second = add.add(draft(), purchase = null) as AddWithoutCatalogResult.Added

        assertThat(first.teaId).isNotEqualTo(second.teaId)
        assertThat(data.teas.observeTeas().first()).hasSize(2)
    }
}
