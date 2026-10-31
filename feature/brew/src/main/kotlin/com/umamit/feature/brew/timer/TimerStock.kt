package com.umamit.feature.brew.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.text.stockGramsText
import com.umamit.core.model.StockLevel
import com.umamit.feature.brew.R

/**
 * The session tea's stock on READY (BRW-11, D-098): [grams] left and its INV-05 [level], by the tea's own
 * „Knapp ab" first, as in the setup (BRW-08, D-097).
 */
@Immutable
data class TimerStock(val grams: Double, val level: StockLevel)

/** A line of READY's kettle hint as it shows (BRW-11) and as TalkBack reads it. */
internal class SpokenLine(val text: String, val description: String)

/**
 * „38 g übrig", „14 g übrig · knapp" or „Leer": the setup's stock text (BRW-08, D-097) for READY (BRW-11,
 * D-098). TalkBack reads „14,5 Gramm übrig, knapp".
 */
@Composable
internal fun stockLine(stock: TimerStock): SpokenLine {
    val grams = stockGramsText(stock.grams)
    return when (stock.level) {
        StockLevel.EMPTY -> stringResource(R.string.brew_setup_stock_empty).let { SpokenLine(it, it) }

        StockLevel.LOW -> SpokenLine(
            text = stringResource(R.string.brew_setup_stock_low, grams),
            description = stringResource(R.string.brew_timer_stock_low_description, grams),
        )

        StockLevel.AVAILABLE -> SpokenLine(
            text = stringResource(R.string.brew_setup_stock, grams),
            description = stringResource(R.string.brew_timer_stock_description, grams),
        )
    }
}
