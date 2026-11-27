package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.onboarding.R

/** The top of a tea's detail (SCR-09): ← and ⋮ over the hero, the tea's name and its Sorte. */
@Composable
private fun TeaDetailHead() {
    Column {
        val tea = MockupDemo.morgentau
        Box {
            MockupHero(tea)
            Row(
                Modifier.fillMaxWidth().padding(UmamiSpacing.XXSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MockupIcon(UmamiIcons.ArrowBack)
                MockupIcon(UmamiIcons.MoreVert)
            }
        }
        Column(Modifier.padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall)) {
            Text(
                stringResource(tea.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(tea.sorte),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StockButtons() {
    Row(
        Modifier.padding(horizontal = UmamiSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
    ) {
        Text(
            stringResource(R.string.mockup_collection_tea_stock_purchase),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.mockup_collection_tea_stock_correct),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** M-STOCK: a tea's detail, *Vorrat* with the stock and the cost lines marked. */
@Composable
internal fun StockMockup() {
    MockupScreen {
        TeaDetailHead()
        Column(Modifier.mockupMarker()) {
            SectionHeader(stringResource(R.string.mockup_collection_tea_section_stock))
            Column(Modifier.padding(horizontal = UmamiSpacing.Medium)) {
                StockLine(
                    stringResource(
                        R.string.mockup_collection_tea_stock,
                        GermanFormat.gramsTrimmed(MockupDemo.STOCK_GRAMS),
                    ),
                )
                StockLine(stringResource(R.string.mockup_collection_tea_cost_price, MockupDemo.pricePer100g()))
                StockLine(stringResource(R.string.mockup_collection_tea_cost_recipe, MockupDemo.costPerLiter()))
            }
            MockupGap(height = UmamiSpacing.XSmall)
        }
        StockButtons()
        MockupGap()
        MockupBars(listOf(BAR_LONG, BAR_MEDIUM), Modifier.padding(horizontal = UmamiSpacing.Medium))
    }
}

@Composable
private fun StockLine(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = UmamiSpacing.XXSmall),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** M-HISTORY: the Vorrat history below the buttons, a *Kauf* row marked. */
@Composable
internal fun HistoryMockup() {
    MockupScreen {
        MockupTopBar(title = stringResource(MockupDemo.morgentau.name), back = true)
        SectionHeader(stringResource(R.string.mockup_collection_tea_section_stock))
        StockButtons()
        MockupGap(height = UmamiSpacing.XSmall)
        HistoryRow(R.string.mockup_collection_tea_history_purchase, purchaseAmount(), Modifier.mockupMarker())
        HistoryRow(R.string.mockup_collection_tea_history_brew, "$MINUS$BREW_GRAMS g")
        HistoryRow(R.string.mockup_collection_tea_history_correction, "$MINUS$CORRECTION_GRAMS g")
        HistoryRow(R.string.mockup_collection_tea_history_purchase, purchaseAmount())
    }
}

private fun purchaseAmount() = "+$PURCHASE_GRAMS g · ${GermanFormat.euros(PURCHASE_CENTS)} €"

/** One history row: the kind over a blank bar for the day, the amount at the end. */
@Composable
private fun HistoryRow(kind: Int, amount: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(kind),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MockupGap(height = UmamiSpacing.XXSmall)
                MockupBar(DAY_BAR)
            }
            Text(amount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** M-RECIPES: a tea's detail, *Rezepte* with the recipe's pencil and *Rezept hinzufügen* marked. */
@Composable
internal fun RecipesMockup() {
    MockupScreen {
        TeaDetailHead()
        MockupGap(height = UmamiSpacing.XSmall)
        MockupBars(listOf(BAR_LONG), Modifier.padding(horizontal = UmamiSpacing.Medium))
        Column(Modifier.mockupMarker()) {
            SectionHeader(stringResource(R.string.mockup_collection_tea_section_recipes))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.mockup_collection_tea_recipe_default),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${MockupDemo.leaf()} · ${MockupDemo.water()} · ${MockupDemo.temperature()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MockupIcon(UmamiIcons.Edit)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = UmamiSpacing.Medium, vertical = UmamiSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            ) {
                Icon(painterResource(UmamiIcons.Add), null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.mockup_collection_tea_recipe_add),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val PURCHASE_GRAMS = 100

/** One package at the demo price: „+100 g · 12,40 €". */
private const val PURCHASE_CENTS = MockupDemo.PRICE_CENTS_PER_100_G
private const val BREW_GRAMS = 5
private const val CORRECTION_GRAMS = 15
private const val MINUS = "−"
private val BAR_LONG = 220.dp
private val BAR_MEDIUM = 160.dp
private val DAY_BAR = 70.dp
