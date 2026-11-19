package com.umamit.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.ExactPriceSession
import com.umamit.core.data.catalog.ExactPrices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The add sheet (SCR-23, CAT-12) for the catalog tea [catalogKey] where another screen writes the purchase, as
 * SCR-32's *Neuen Tee anlegen* „aus dem Katalog" does (PRF-08): the same sizes, price (with its one exact-price
 * fetch per size, GEN-01 (c)) and date, but *Aufnehmen* only hands the [CatalogPurchase] to [onSave]; [saving] and
 * [failed] are the caller's. [onDismiss] closes it without a write (the caller ignores it while [saving]).
 */
@Composable
fun CatalogPackageSheet(
    catalogKey: String,
    saving: Boolean,
    failed: Boolean,
    onSave: (CatalogPurchase) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CatalogPackageSheetViewModel = hiltViewModel(),
) {
    // One sheet per opening: a configuration change keeps the input, a new opening starts afresh.
    val opening = rememberSaveable { System.nanoTime() }
    LaunchedEffect(viewModel, catalogKey, opening) { viewModel.open(catalogKey, opening) }
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    // Only this opening's sheet: a dismissed one's input never shows again.
    val open = sheet?.takeIf { it.catalogKey == catalogKey && it.opening == opening }?.state ?: return
    CatalogAddSheet(
        state = open.copy(saving = saving, failed = failed && !saving),
        actions = AddSheetActions(
            onSelectSize = viewModel::selectSize,
            onPriceChange = viewModel::editPrice,
            onGramsChange = viewModel::editGrams,
            onDateChange = viewModel::pickDate,
            onSave = { viewModel.purchase()?.let(onSave) },
        ),
        onDismissRequest = onDismiss,
    )
}

/** The input of [CatalogPackageSheet]: [AddSheetState] for one catalog tea, with its exact-price fetches; no write. */
@HiltViewModel
class CatalogPackageSheetViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val exactPrices: ExactPrices,
    private val clock: WallClock,
) : ViewModel() {
    /** The sheet of [catalogKey], opened as [opening]. */
    data class Sheet(val catalogKey: String, val opening: Long, val state: AddSheetState)

    private val sheetState = MutableStateFlow<Sheet?>(null)

    /** The open sheet, once its catalog tea was read; null for a tea the catalog no longer has. */
    val sheet: StateFlow<Sheet?> = sheetState.asStateFlow()

    private var prices: ExactPriceSession? = null
    private val fetches = mutableListOf<Job>()
    private var opened: Pair<String, Long>? = null

    /** Opens the sheet of [catalogKey] for [opening] with today's date; the same opening is read once. */
    fun open(catalogKey: String, opening: Long) {
        if (opened == catalogKey to opening) return
        opened = catalogKey to opening
        fetches.forEach { it.cancel() }
        fetches.clear()
        sheetState.value = null
        viewModelScope.launch {
            val tea = catalog.observeTea(catalogKey).first() ?: return@launch
            prices = exactPrices.session()
            sheetState.value = Sheet(
                catalogKey = catalogKey,
                opening = opening,
                state = AddSheetState.open(
                    teaName = tea.name,
                    packages = tea.packages,
                    repurchase = false,
                    lastBoughtGrams = null,
                    today = clock.today(),
                ),
            )
            fetchSelected()
        }
    }

    /** Chooses size [index] (`sizes.size`: a custom amount) and fetches its exact price when it needs one. */
    fun selectSize(index: Int) {
        change { it.select(index) }
        fetchSelected()
    }

    fun editPrice(text: String) = change { it.editPrice(text) }

    fun editGrams(text: String) = change { it.editGrams(text) }

    fun pickDate(day: LocalDate) = change { it.pickDate(day) }

    /** *Aufnehmen*: the purchase as entered, or null while the amount or the price is no valid number. */
    fun purchase(): CatalogPurchase? = sheetState.value?.let { sheet ->
        val state = sheet.state.copy(saving = false).takeIf { it.canSave }
        state?.grams?.let { grams -> CatalogPurchase(sheet.catalogKey, grams, state.priceCents, state.date) }
    }

    private fun change(edit: (AddSheetState) -> AddSheetState) =
        sheetState.update { sheet -> sheet?.copy(state = edit(sheet.state)) }

    /** One fetch per size and sheet, only for an estimate or a missing price with its own page. */
    private fun fetchSelected() {
        val sheet = sheetState.value ?: return
        val index = sheet.state.selected
        val size = sheet.state.selectedSize?.takeIf { it.needsFetch }
        val url = size?.variantUrl
        val session = prices
        if (size != null && url != null && session != null) {
            change { it.fetching(index) }
            fetches += viewModelScope.launch {
                val cents = session.price(url, size.grams)
                change { it.fetched(index, cents) }
            }
        }
    }
}
