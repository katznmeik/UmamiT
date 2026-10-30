package com.umamit.core.testing

import com.umamit.core.data.LowStockRepository
import com.umamit.core.model.LowStockCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LowStockRepository]: tests set [card]; *Ausblenden* records the card and clears the slot. */
class FakeLowStockRepository(initial: LowStockCard? = null) : LowStockRepository {
    val card = MutableStateFlow(initial)

    /** The cards hidden so far, in order. */
    val hidden = mutableListOf<LowStockCard>()

    override fun observeCard(): Flow<LowStockCard?> = card

    override suspend fun hide(card: LowStockCard) {
        hidden += card
        if (this.card.value == card) this.card.value = null
    }
}

/** „*Tanabata* wird knapp · 14 g übrig" with INV-09's link (synthetic, D-016). */
fun lowStockCard(
    teaName: String = "Tanabata",
    grams: Double = 14.0,
    reorderUrl: String? = "https://example.invalid/tanabata-50",
    teaId: String = "tanabata",
) = LowStockCard(
    teaId = teaId,
    teaName = teaName,
    styleKey = "sencha",
    remainingGrams = grams,
    reorderUrl = reorderUrl,
    hideKey = "$teaId/buy-1",
)
