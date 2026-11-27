package com.umamit.feature.onboarding.howto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.onboarding.R
import com.umamit.feature.onboarding.mockup.HelpMockup
import com.umamit.feature.onboarding.mockup.HelpMockupSize

private const val TITLE_MAX_LINES = 3

/**
 * One Anleitung (SCR-36; ONB-08): ← and the topic's title, then a scrolling page with the intro, the numbered steps
 * (the number in a small matcha circle, the text beside it), the mock-up under each step that has one (its marker
 * carrying the step's number) and the „Tipp" line where there is one. TalkBack reads the title as a heading, the
 * intro, each step as „Schritt 2 von 5: …" and each mock-up's description. The Anleitungen keep no read state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToTopicScreen(topic: HowToTopic, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(topic.title),
                        modifier = Modifier.semantics { heading() },
                        maxLines = TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                // The bar grows with a wrapping title instead of clipping it at font 2.0 (SCR-36).
                expandedHeight = Dp.Unspecified,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium)
                .testTag(HowToTags.TOPIC),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
        ) {
            Text(
                stringResource(topic.intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            topic.steps.forEachIndexed { index, step ->
                HowToStepItem(number = index + 1, count = topic.steps.size, step = step)
            }
            topic.tip?.let { HowToTip(it) }
        }
    }
}

/** A numbered step: the circle and the text beside it, read as one item; its mock-up (if any) under the text. */
@Composable
private fun HowToStepItem(number: Int, count: Int, step: HowToStep) {
    val text = stringResource(step.text)
    val description = stringResource(R.string.howto_step_description, number, count, text)
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall)) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            StepNumber(number)
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        step.mockup?.let {
            HelpMockup(
                id = it,
                size = HelpMockupSize.Guide,
                modifier = Modifier.padding(start = NUMBER_SIZE + UmamiSpacing.Small),
                markerNumber = number,
            )
        }
    }
}

/** The step's number in a small matcha circle. */
@Composable
private fun StepNumber(number: Int) {
    Box(
        Modifier.size(NUMBER_SIZE).background(DefaultExtendedColors.alert.matcha, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = DefaultExtendedColors.alert.content,
            textAlign = TextAlign.Center,
        )
    }
}

/** The „Tipp" line at the end of a page. */
@Composable
private fun HowToTip(tip: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall)) {
        Text(
            stringResource(R.string.howto_tip_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(tip),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val NUMBER_SIZE = 24.dp
