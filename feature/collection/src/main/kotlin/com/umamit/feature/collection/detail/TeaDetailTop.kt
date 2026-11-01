package com.umamit.feature.collection.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.StatFigure
import com.umamit.core.designsystem.component.StatTriplet
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.UmamiLevel
import com.umamit.feature.collection.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Names, the *Sorte* chip with the Bio and Aromatisiert flags, and the umami meter. */
@Composable
internal fun Header(tea: TeaDetail) {
    Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin).padding(top = UmamiSpacing.Large)) {
        Text(
            text = tea.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        tea.secondaryName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val tags = listOfNotNull(
            tea.teaType,
            stringResource(R.string.collection_tea_organic).takeIf { tea.details.isOrganic },
            stringResource(R.string.collection_tea_flavored).takeIf { tea.details.isFlavored },
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(UmamiSpacing.Small))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
                verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
            ) {
                tags.forEach { Tag(it) }
            }
        }
        tea.umamiLevel?.let {
            Spacer(Modifier.height(UmamiSpacing.Small))
            UmamiMeter(it)
        }
    }
}

@Composable
internal fun Tag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XXSmall),
        )
    }
}

/** „Umami" with four leaves in the umami colour (03 §3.1 `tertiary`); TalkBack reads „Umami: hoch". */
@Composable
private fun UmamiMeter(level: UmamiLevel) {
    val description = stringResource(R.string.collection_tea_umami_description, stringResource(level.label))
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.collection_tea_umami),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(UmamiSpacing.Small))
        Row(horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall)) {
            repeat(UMAMI_LEAVES) { index ->
                Icon(
                    painter = painterResource(UmamiIcons.Leaf),
                    contentDescription = null,
                    tint = if (index < level.ordinal) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_LEAF_ALPHA)
                    },
                    modifier = Modifier.size(LEAF_SIZE),
                )
            }
        }
    }
}

private val UmamiLevel.label: Int
    get() = when (this) {
        UmamiLevel.NONE -> R.string.collection_tea_umami_none
        UmamiLevel.LOW -> R.string.collection_tea_umami_low
        UmamiLevel.MEDIUM -> R.string.collection_tea_umami_medium
        UmamiLevel.HIGH -> R.string.collection_tea_umami_high
        UmamiLevel.VERY_HIGH -> R.string.collection_tea_umami_very_high
    }

/** The `StatTriplet` of SCR-09 (D-100): the tea's sessions, last brew and stock. */
@Composable
internal fun Stats(tea: TeaDetail) {
    val sessionsLabel = stringResource(R.string.collection_tea_stat_sessions)
    val stockText = stockText(tea.stockGrams, tea.stockLevel, short = true)
    val lastBrewed = tea.lastBrewed?.format(DAY)
    StatTriplet(
        first = StatFigure(
            icon = painterResource(UmamiIcons.Journal),
            value = tea.sessionCount.toString(),
            label = sessionsLabel,
            description = pluralStringResource(
                R.plurals.collection_tea_stat_sessions_description,
                tea.sessionCount,
                tea.sessionCount,
            ),
        ),
        second = StatFigure(
            icon = painterResource(UmamiIcons.Schedule),
            value = lastBrewed ?: stringResource(R.string.collection_tea_stat_never),
            label = stringResource(R.string.collection_tea_stat_last),
            description = lastBrewed?.let { stringResource(R.string.collection_tea_stat_last_description, it) }
                ?: stringResource(R.string.collection_tea_stat_never_description),
        ),
        third = StatFigure(
            icon = painterResource(UmamiIcons.Leaf),
            value = stockText,
            label = stringResource(R.string.collection_tea_stat_stock),
            description = stringResource(R.string.collection_tea_stat_stock_description, stockText),
        ),
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium),
    )
}

/** „21. Okt. 2026" (German date format, CLAUDE.md rule 4). */
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** The meter's leaves: LOW fills one, VERY_HIGH all four, NONE none (05 §5). */
private const val UMAMI_LEAVES = 4
private const val EMPTY_LEAF_ALPHA = 0.3f
private val LEAF_SIZE = 20.dp
