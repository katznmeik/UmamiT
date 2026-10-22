package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.text.germanUppercase
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * Section overline with a hairline and an optional „Alle anzeigen" (03 §3.8).
 * The [title] is given in sentence case and shown uppercased ẞ-safely; TalkBack
 * reads the sentence-case title as a heading.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onShowAll: (() -> Unit)? = null) {
    Row(
        // „Alle anzeigen" brings its own 48 dp touch target; without it the header stays compact.
        modifier = modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted, so the unweighted „Alle anzeigen" is measured first and never squeezed out;
        // inside, the overline takes what it needs (wrapping at large font scales) and the
        // hairline fills the rest.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.germanUppercase(),
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = title
                },
                style = UmamiTheme.textStyles.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(UmamiSpacing.Small))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = UmamiSpacing.Hairline,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (onShowAll != null) {
            val description = stringResource(R.string.ds_section_show_all_description, title)
            TextButton(
                onClick = onShowAll,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(
                    text = stringResource(R.string.ds_section_show_all),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
