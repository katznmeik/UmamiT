package com.umamit.feature.collection

import com.umamit.core.model.CollectionFilter
import com.umamit.core.model.Freshness
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TeaCategory

/** Synthetic *Meine Tees* states for the screenshots (D-016: invented teas, placeholder images). */
internal object MyTeasStates {
    private fun row(
        id: String,
        name: String,
        sorte: String?,
        secondaryName: String? = null,
        grams: Double? = null,
        level: StockLevel? = null,
        freshness: Freshness? = null,
        isFavorite: Boolean = false,
        checkRecipe: Boolean = false,
        isArchived: Boolean = false,
        rating: Double? = null,
    ) = MyTeaRow(
        id = id,
        name = name,
        secondaryName = secondaryName,
        sorte = sorte,
        category = TeaCategory.GREEN,
        remainingGrams = grams,
        stockLevel = level,
        freshness = freshness,
        isFavorite = isFavorite,
        checkRecipe = checkRecipe,
        isArchived = isArchived,
        photoPath = null,
        rating = rating,
    )

    private val rows = listOf(
        row(
            "1",
            "Morgentau",
            "Sencha",
            "Asamushi aus Shizuoka",
            38.0,
            StockLevel.AVAILABLE,
            Freshness.FRESH,
            isFavorite = true,
            rating = 4.5,
        ),
        row(
            "2",
            "Mondschatten",
            "Gyokuro",
            "Beschattet, 21 Tage",
            8.0,
            StockLevel.LOW,
            Freshness.DRINK_SOON,
            rating = 4.0,
        ),
        row("3", "Flusskiesel", "Kabusecha", grams = 0.0, level = StockLevel.EMPTY, checkRecipe = true),
        row("4", "Herbstwind", "Hojicha", "Geröstet", 120.0, StockLevel.AVAILABLE, Freshness.PAST_PEAK),
        row("5", "Bergnebel", "Genmaicha", isFavorite = true),
        row("6", "Kirschblüte", "Sencha", "Aromatisiert", 55.5, StockLevel.AVAILABLE, rating = 3.5),
    )

    val list = MyTeasUiState(
        loading = false,
        rows = rows,
        categories = listOf(TeaCategory.GREEN),
        sorten = listOf("Gyokuro", "Hojicha", "Kabusecha", "Sencha"),
        ownedCount = rows.size,
        archivedCount = 1,
    )

    val archived = list.copy(
        rows = listOf(row("9", "Alter Bancha", "Bancha", isArchived = true)),
        filter = CollectionFilter(archived = true),
    )

    val noTeas = MyTeasUiState(loading = false)

    val noMatch = list.copy(rows = emptyList(), filter = CollectionFilter(search = "Matcha"))
}
