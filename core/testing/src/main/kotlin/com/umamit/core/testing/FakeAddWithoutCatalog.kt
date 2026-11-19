package com.umamit.core.testing

import android.database.SQLException
import com.umamit.core.data.AddWithoutCatalog
import com.umamit.core.data.AddWithoutCatalogResult
import com.umamit.core.data.RoomAddWithoutCatalog
import com.umamit.core.data.TeaPurchase
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea

/**
 * [AddWithoutCatalog] on a [FakeCollection], as on Room: a new tea `manual-<n>` without photo, archive
 * flag or „Rezept prüfen", its style's recipes (the default first; *Westlich* 3 g / 250 ml / 85 °C / 3:00
 * without a known style) and an INITIAL event only with a package, then the block in the same „transaction" (a
 * throw undoes the add; a [SQLException] answers Failed). Records every [added] call; [failNext] makes the next
 * add fail.
 */
class FakeAddWithoutCatalog(private val collection: FakeCollection) : AddWithoutCatalog {
    val added = mutableListOf<Pair<Tea, TeaPurchase?>>()

    var failNext = false

    override suspend fun add(
        tea: Tea,
        purchase: TeaPurchase?,
        inTransaction: suspend (teaId: String) -> Unit,
    ): AddWithoutCatalogResult {
        if (failNext) {
            failNext = false
            return AddWithoutCatalogResult.Failed
        }
        return try {
            collection.rollingBack { write(tea, purchase).also { inTransaction(it) } }
                .let(AddWithoutCatalogResult::Added)
        } catch (_: SQLException) {
            AddWithoutCatalogResult.Failed
        }
    }

    private suspend fun write(tea: Tea, purchase: TeaPurchase?): String {
        added += tea to purchase
        val teaId = "manual-${added.size}"
        collection.teas.saveTea(tea.copy(id = teaId, isArchived = false, coverPhotoId = null, recipeCheckReason = null))
        val templates =
            tea.styleKey?.let(BuiltInStyles::byKey)?.recipes ?: listOf(RoomAddWithoutCatalog.WESTERN_DEFAULT)
        templates.sortedByDescending { it.isDefault }.forEachIndexed { position, template ->
            collection.recipes.saveRecipe(
                template.copy(isDefault = position == 0).toRecipe("$teaId-recipe-$position", teaId, position),
            )
        }
        purchase?.let {
            collection.stock.addEvent(
                InventoryEvent(
                    id = "$teaId-initial",
                    teaId = teaId,
                    type = InventoryEventType.INITIAL,
                    deltaGrams = it.grams,
                    occurredAtMillis = added.size.toLong(),
                    priceCents = it.priceCents,
                ),
            )
        }
        return teaId
    }
}
