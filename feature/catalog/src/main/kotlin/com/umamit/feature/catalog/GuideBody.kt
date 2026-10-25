package com.umamit.feature.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.GuideBlock
import com.umamit.core.model.GuideDocument
import com.umamit.core.model.GuideInline

/**
 * A guide's converted article (SCR-20, 08 §2.7): headings in the serif, paragraphs with bold,
 * italics and links, lists, and the images from local files only (NFR-SEC-04). A link reports
 * its stored target to [onLink]; where it leads is the caller's decision.
 *
 * @param image the painter of a local image file; tests pass placeholder images.
 */
@Composable
internal fun GuideBody(
    document: GuideDocument,
    onLink: (String) -> Unit,
    image: @Composable (path: String) -> Painter?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
        document.blocks.forEach { block ->
            when (block) {
                is GuideBlock.Heading -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = UmamiSpacing.Small)
                        .semantics { heading() },
                )

                is GuideBlock.Paragraph -> Text(
                    text = rememberGuideText(block.inlines, onLink),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is GuideBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XXSmall)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall)) {
                            Text(
                                text = if (block.ordered) "${index + 1}." else BULLET,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = MARKER_WIDTH),
                            )
                            Text(
                                text = rememberGuideText(item, onLink),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                is GuideBlock.Image -> image(block.source)?.let { GuideImage(it, block.alt) }
            }
        }
    }
}

/** An image at its own aspect ratio (4:3 until it is known), full width, with soft corners. */
@Composable
private fun GuideImage(painter: Painter, alt: String) {
    val size = painter.intrinsicSize
    val ratio = if (size.isSpecified && size.width > 0f && size.height > 0f) size.width / size.height else DEFAULT_RATIO
    Image(
        painter = painter,
        contentDescription = alt.ifEmpty { null },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .padding(vertical = UmamiSpacing.XSmall)
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
            .clip(RoundedCornerShape(IMAGE_CORNER)),
    )
}

/** Styled text with its links; each link reports its target to [onLink]. */
@Composable
private fun rememberGuideText(inlines: List<GuideInline>, onLink: (String) -> Unit): AnnotatedString {
    val linkColor: Color = MaterialTheme.colorScheme.primary
    return remember(inlines, linkColor, onLink) {
        val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            inlines.forEach { inline ->
                when (inline) {
                    GuideInline.LineBreak -> append('\n')

                    is GuideInline.Span -> {
                        val style = SpanStyle(
                            fontWeight = if (inline.bold) FontWeight.SemiBold else null,
                            fontStyle = if (inline.italic) FontStyle.Italic else null,
                        )
                        val target = inline.link
                        if (target == null) {
                            withStyle(style) { append(inline.text) }
                        } else {
                            val link = LinkAnnotation.Clickable(target, linkStyles) { onLink(target) }
                            withLink(link) { withStyle(style) { append(inline.text) } }
                        }
                    }
                }
            }
        }
    }
}

private const val BULLET = "•"
private const val DEFAULT_RATIO = 4f / 3f
private const val MIN_RATIO = 0.5f
private const val MAX_RATIO = 3f
private val MARKER_WIDTH = 16.dp
private val IMAGE_CORNER = 8.dp
