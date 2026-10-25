package com.umamit.feature.catalog

import com.umamit.core.model.CatalogPackage
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

/** Where a size's price stands while the sheet is open (CAT-12, GEN-01 (c)). */
enum class PriceFetch {
    /** Not fetched: the shop's price from the refresh is exact, the size has no page, or it was not selected yet. */
    IDLE,

    /** The size's page is being fetched. */
    RUNNING,

    /** The exact price came from the size's page. */
    EXACT,

    /** Offline or stopped: the estimate (or no price) stays. */
    FAILED,
}

/** What the sheet says under the price (SCR-23). */
enum class PriceHint {
    /** The exact price is being fetched; the estimate shows meanwhile. */
    FETCHING,

    /** The shop's price. */
    SHOP,

    /** An estimate („≈"), editable. */
    ESTIMATE,

    /** No price known; one can be entered. */
    NONE,
}

/**
 * A package size the sheet offers: the catalog's [CatalogPackage] with its price as it stands,
 * an estimate replaced by the exact price once fetched ([fetch]).
 */
data class AddSheetSize(
    val grams: Double,
    val priceCents: Long?,
    val estimate: Boolean,
    val savingsPercent: Int?,
    val variantUrl: String?,
    val fetch: PriceFetch = PriceFetch.IDLE,
) {
    /** The price is an estimate or missing and the size has its own page: worth one fetch. */
    val needsFetch: Boolean get() = fetch == PriceFetch.IDLE && variantUrl != null && (estimate || priceCents == null)
}

/**
 * *In Sammlung aufnehmen* / *Nachkauf erfassen* (SCR-23, CAT-12): the package size (one of
 * [sizes], or a custom amount at index `sizes.size`), its price as editable text, the purchase
 * date. Pure: every change returns a new state.
 *
 * @property repurchase the tea is in the collection already: the sheet records a repurchase.
 * @property priceEdited the user typed the price: an exact price arriving later no longer replaces it.
 * @property saving the purchase is being written.
 * @property failed the last save failed; the sheet stays open.
 */
data class AddSheetState(
    val teaName: String,
    val repurchase: Boolean,
    val sizes: List<AddSheetSize>,
    val selected: Int,
    val price: String,
    val date: LocalDate,
    val today: LocalDate,
    val customGrams: String = "",
    val priceEdited: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    /** The custom amount is chosen. */
    val custom: Boolean get() = selected == sizes.size

    val selectedSize: AddSheetSize? get() = sizes.getOrNull(selected)

    /** The grams to record, or null while the custom amount is no valid number. */
    val grams: Double? get() = selectedSize?.grams ?: parseGrams(customGrams)

    /** The price is empty or a valid amount. */
    val priceValid: Boolean get() = price.isBlank() || parseCents(price) != null

    /** The price to record: null when empty. */
    val priceCents: Long? get() = parseCents(price)

    /** Saving never waits for a price fetch (GEN-01 (c)). */
    val canSave: Boolean get() = !saving && grams != null && priceValid

    /** What the sheet says under the price; null for a price the user entered. */
    val hint: PriceHint? get() {
        val size = selectedSize
        return when {
            size == null || priceEdited -> null
            size.fetch == PriceFetch.RUNNING -> PriceHint.FETCHING
            size.priceCents == null -> PriceHint.NONE
            size.estimate -> PriceHint.ESTIMATE
            else -> PriceHint.SHOP
        }
    }

    /** Chooses size [index] (`sizes.size`: the custom amount); its price replaces the field's. */
    fun select(index: Int): AddSheetState = copy(
        selected = index.coerceIn(0, sizes.size),
        price = sizes.getOrNull(index)?.priceCents?.let(CatalogFormat::euros).orEmpty(),
        priceEdited = false,
        failed = false,
    )

    fun editPrice(text: String): AddSheetState = copy(price = text, priceEdited = true, failed = false)

    fun editGrams(text: String): AddSheetState = copy(customGrams = text, failed = false)

    /** Sets the purchase date; never after [today]. */
    fun pickDate(day: LocalDate): AddSheetState = copy(date = minOf(day, today), failed = false)

    fun fetching(index: Int): AddSheetState = updateSize(index) { it.copy(fetch = PriceFetch.RUNNING) }

    /**
     * The fetch of size [index] ended with [cents] (null: offline or stopped). An exact price
     * replaces the estimate, and the field's price while it shows that size unedited.
     */
    fun fetched(index: Int, cents: Long?): AddSheetState {
        val next = updateSize(index) {
            if (cents == null) {
                it.copy(fetch = PriceFetch.FAILED)
            } else {
                it.copy(priceCents = cents, estimate = false, fetch = PriceFetch.EXACT)
            }
        }
        return if (cents != null && index == selected &&
            !priceEdited
        ) {
            next.copy(price = CatalogFormat.euros(cents))
        } else {
            next
        }
    }

    private fun updateSize(index: Int, change: (AddSheetSize) -> AddSheetSize): AddSheetState =
        copy(sizes = sizes.mapIndexed { i, size -> if (i == index) change(size) else size })

    companion object {
        private const val MAX_GRAMS = 10_000.0
        private const val CENT_DIGITS = 2
        private val AMOUNT = Regex("""\d{1,6}(?:[.,]\d{1,2})?""")
        private val GRAMS = Regex("""\d{1,5}(?:[.,]\d)?""")
        private const val SAME_GRAMS = 0.01

        /**
         * The sheet for [teaName] with its [packages]: on a repurchase the size bought last
         * ([lastBoughtGrams]) is chosen, else the size whose exact price the shop showed, else
         * the first with a price; without packages, the custom amount. Bought [today].
         */
        fun open(
            teaName: String,
            packages: List<CatalogPackage>,
            repurchase: Boolean,
            lastBoughtGrams: Double?,
            today: LocalDate,
        ): AddSheetState {
            val sizes = packages.map {
                AddSheetSize(it.grams, it.priceCents, it.priceIsEstimate, it.savingsPercent, it.variantUrl)
            }
            val bought = lastBoughtGrams?.let { grams -> sizes.indexOfFirst { abs(it.grams - grams) < SAME_GRAMS } }
            val selected = bought?.takeIf { it >= 0 }
                ?: sizes.indexOfFirst { it.priceCents != null && !it.estimate }.takeIf { it >= 0 }
                ?: sizes.indexOfFirst { it.priceCents != null }.takeIf { it >= 0 }
                // The first size, or the custom amount (index 0) when there is none.
                ?: 0
            return AddSheetState(
                teaName = teaName,
                repurchase = repurchase,
                sizes = sizes,
                selected = 0,
                price = "",
                date = today,
                today = today,
            ).select(selected)
        }

        /** „12,40", „12.4", „12" → cents; null when blank or no amount. */
        fun parseCents(text: String): Long? {
            val bare = text.replace("€", "").trim()
            if (!AMOUNT.matches(bare)) return null
            return BigDecimal(bare.replace(',', '.')).movePointRight(CENT_DIGITS).toLong()
        }

        /** „250", „37,5" → grams in (0, 10 000]; null otherwise. */
        fun parseGrams(text: String): Double? {
            val bare = text.replace("g", "").trim()
            if (!GRAMS.matches(bare)) return null
            return bare.replace(',', '.').toDouble().takeIf { it > 0.0 && it <= MAX_GRAMS }
        }
    }
}
