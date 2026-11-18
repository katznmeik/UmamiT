package com.umamit.feature.collection.receive

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
 */
class ReceiveRecipeHost(
    val token: Long,
    val bytes: ByteArray,
    val onClose: () -> Unit,
    val onTakenOver: (teaId: String, recipeName: String) -> Unit,
    val alertKey: String? = null,
    val linkMessage: (TeaLinkOutcome) -> Int = { R.string.receive_recipe_failed },
)
