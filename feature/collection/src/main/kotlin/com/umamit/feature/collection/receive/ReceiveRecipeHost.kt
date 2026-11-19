package com.umamit.feature.collection.receive

import androidx.compose.runtime.Composable
import com.umamit.core.data.catalog.CatalogPurchase
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.feature.collection.R

/**
 * What SCR-32 gets from the Sammlung stack.
 *
 * @property token the received file's token; the same token is read once.
 * @property bytes the received file as copied (at most 64 KB + 1 byte); the reader decides.
 * @property onClose ✕, back and the refusal's *Schließen*: nothing was written.
 * @property onTakenOver *Rezept übernehmen* added [recipeName] to [teaId]: the host opens SCR-09 with
 *   „Rezept „…" übernommen".
 * @property alertKey the alert playing now: the tea sheet gives way to another (rule 8).
 * @property linkMessage CAT-17's message of a failed *Tee über Shop-Link laden* (03 §5), from the catalog.
 * @property packageSheet the catalog's add-from-catalog package sheet (SCR-23) for *Neuen Tee anlegen* „aus dem
 *   Katalog", from the app (features don't depend on each other); it only hands back the purchase.
 */
class ReceiveRecipeHost(
    val token: Long,
    val bytes: ByteArray,
    val onClose: () -> Unit,
    val onTakenOver: (teaId: String, recipeName: String) -> Unit,
    val alertKey: String? = null,
    val linkMessage: (TeaLinkOutcome) -> Int = { R.string.receive_recipe_failed },
    val packageSheet: @Composable (PackageSheetRequest) -> Unit = {},
)

/**
 * What SCR-32 asks of the package sheet: the catalog tea [catalogKey]; [saving] while the tea, its package and the
 * recipe are written, [failed] after a failed write. [onSave] gets the purchase as entered (the sheet writes
 * nothing), [onDismiss] closes it without a write.
 */
class PackageSheetRequest(
    val catalogKey: String,
    val saving: Boolean,
    val failed: Boolean,
    val onSave: (CatalogPurchase) -> Unit,
    val onDismiss: () -> Unit,
)
