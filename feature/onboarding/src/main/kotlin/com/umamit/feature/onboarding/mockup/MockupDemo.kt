package com.umamit.feature.onboarding.mockup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.component.TeaThumbnail
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.onboarding.R
import com.umamit.core.designsystem.R as DsR

/** A demo tea: its name, its Sorte (both the same in every language) and the 08 §2 style key of its type colour. */
class DemoTea(@StringRes val name: Int, @StringRes val sorte: Int, val styleKey: String)

/**
 * The demo data of every mock-up (SCR-36 „Demo data"), invented: never a shop name, text or photo (CLAUDE.md
 * rule 3). Names stay as written in both languages; the numbers follow the app language through [GermanFormat]
 * („5,0 g" / „5.0 g"). A tea's photo is its type leaf on paper ([DemoLeaf]).
 */
object MockupDemo {
    val morgentau = DemoTea(R.string.mockup_demo_tea_morgentau, R.string.mockup_demo_sorte_sencha, "sencha")
    val nebelhang = DemoTea(R.string.mockup_demo_tea_nebelhang, R.string.mockup_demo_sorte_gyokuro, "gyokuro")
    val herbstfeuer = DemoTea(R.string.mockup_demo_tea_herbstfeuer, R.string.mockup_demo_sorte_genmaicha, "genmaicha")
    val teas = listOf(morgentau, nebelhang, herbstfeuer)

    @StringRes val vesselName = R.string.mockup_demo_vessel
    const val VESSEL_ML = 200
    const val LEAF_GRAMS = 5.0
    const val WATER_ML = 150
    const val TEMPERATURE_CELSIUS = 70
    const val STEEP_SECONDS = 60
    const val STOCK_GRAMS = 38.0
    const val PRICE_CENTS_PER_100_G = 1_240L
    const val COST_PER_LITER_CENTS = 160L

    /** „5,0 g" / „5.0 g". */
    fun leaf(): String = "${GermanFormat.gramsOneDecimal(LEAF_GRAMS)} g"

    /** „150 ml". */
    fun water(): String = "$WATER_ML ml"

    /** „Kleine Kyusu · 200 ml". */
    @Composable
    @ReadOnlyComposable
    fun vessel(): String = "${stringResource(vesselName)} · $VESSEL_ML ml"

    /** „70 °C", from the design system's own string. */
    @Composable
    @ReadOnlyComposable
    fun temperature(): String = stringResource(DsR.string.ds_stat_temperature_value, TEMPERATURE_CELSIUS)

    /** „1:00". */
    fun steepTime(): String = clockText(STEEP_SECONDS)

    /** „38 g übrig" / „38 g left". */
    @Composable
    @ReadOnlyComposable
    fun stockLeft(): String = stringResource(R.string.mockup_my_teas_stock_left, GermanFormat.gramsTrimmed(STOCK_GRAMS))

    /** „12,40" / „12.40" (euros, no sign) per 100 g. */
    fun pricePer100g(): String = GermanFormat.euros(PRICE_CENTS_PER_100_G)

    /** „1,60" / „1.60" (euros, no sign) per litre. */
    fun costPerLiter(): String = GermanFormat.euros(COST_PER_LITER_CENTS)
}

/** The type colour of [tea]. */
@Composable
@ReadOnlyComposable
fun DemoTea.color(): Color = UmamiTheme.extendedColors.teaTypes.forStyle(styleKey)

/** A demo tea's „photo": the type leaf on paper, as a tea without a photo shows it (03 §3.5). */
@Composable
fun DemoLeaf(tea: DemoTea, modifier: Modifier = Modifier) {
    TeaThumbnail(thumbnail = null, placeholderColor = tea.color(), modifier = modifier)
}
