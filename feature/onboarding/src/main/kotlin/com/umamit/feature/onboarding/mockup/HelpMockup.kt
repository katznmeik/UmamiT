package com.umamit.feature.onboarding.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umamit.core.designsystem.theme.DefaultExtendedColors
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * How high a mock-up is drawn (D-137 item 19): at most 200 dp on a tour page (SCR-33), 180 dp in an Anleitung
 * (SCR-36). The width follows from the virtual screen's aspect ([MOCKUP_SCREEN_WIDTH] × [MOCKUP_SCREEN_HEIGHT]).
 */
enum class HelpMockupSize(val height: Dp) {
    Tour(200.dp),
    Guide(180.dp),
}

/**
 * The size of the phone screen a mock-up's content is laid out on, in its own dp. The content is built at this size
 * from the real components and drawn scaled down into the frame, so ring strokes, text and spacing shrink together.
 */
val MOCKUP_SCREEN_WIDTH = 320.dp
val MOCKUP_SCREEN_HEIGHT = 520.dp

/**
 * A mock-up (SCR-36 „Mock-ups", D-137 item 19): a phone-shaped frame (rounded corners, hairline outline, the theme's
 * paper) holding [content], a simplified copy of a screen laid out on a [MOCKUP_SCREEN_WIDTH] × [MOCKUP_SCREEN_HEIGHT]
 * screen and scaled down to [size]. The font scale inside is fixed at 1, so the mock-up keeps its size at font 2.0.
 *
 * The **marker**: each spot the content tags with [Modifier.mockupMarker] gets a matcha ring, drawn at full size over
 * the scaled content; with [markerNumber] (an Anleitung's step) the first ring carries a number badge.
 *
 * Static: a layer on top takes every touch, so nothing inside reacts (a swipe still reaches the pager around it).
 * TalkBack reads [description] and nothing of the content.
 */
@Composable
fun HelpMockup(
    description: String,
    size: HelpMockupSize,
    modifier: Modifier = Modifier,
    markerNumber: Int? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scale = size.height / MOCKUP_SCREEN_HEIGHT
    val width = MOCKUP_SCREEN_WIDTH * scale
    val markers = remember { MockupMarkers() }
    val shape = RoundedCornerShape(FRAME_CORNER)
    Box(
        modifier = modifier
            .size(width, size.height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .border(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline, shape),
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * scale, fontScale = 1f),
                LocalMockupMarkers provides markers,
            ) {
                Box(Modifier.requiredSize(MOCKUP_SCREEN_WIDTH, MOCKUP_SCREEN_HEIGHT)) { content() }
            }
        }
        MarkerOverlay(markers, markerNumber, Modifier.matchParentSize())
    }
}

/** One of the named mock-ups ([HelpMockupId]) in its frame, with its TalkBack description. */
@Composable
fun HelpMockup(id: HelpMockupId, size: HelpMockupSize, modifier: Modifier = Modifier, markerNumber: Int? = null) {
    HelpMockup(
        description = stringResource(id.description),
        size = size,
        modifier = modifier,
        markerNumber = markerNumber,
        content = id.content,
    )
}

/**
 * Tags the spot the mock-up marks; [HelpMockup] draws a matcha ring around it. Several spots get one ring each; the
 * number badge goes on the topmost.
 */
fun Modifier.mockupMarker(): Modifier = this then MockupMarkerElement

private data object MockupMarkerElement : ModifierNodeElement<MockupMarkerNode>() {
    override fun create() = MockupMarkerNode()

    override fun update(node: MockupMarkerNode) = Unit
}

/** Reports its spot's coordinates to the enclosing [HelpMockup] (through [LocalMockupMarkers]). */
private class MockupMarkerNode :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var markers: MockupMarkers? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val target = currentValueOf(LocalMockupMarkers) ?: return
        markers = target
        if (target.spots[this] !== coordinates) target.spots[this] = coordinates
    }

    override fun onDetach() {
        markers?.spots?.remove(this)
        markers = null
    }
}

/** The spots reported by the content. */
internal class MockupMarkers {
    val spots = mutableStateMapOf<Any, LayoutCoordinates>()
}

@Suppress("CompositionLocalAllowlist") // the frame tells the markers inside its content where to report
internal val LocalMockupMarkers = staticCompositionLocalOf<MockupMarkers?> { null }

/** The ring over the content, at real density: a matcha outline [RING_GAP] outside each spot. */
@Composable
private fun MarkerOverlay(markers: MockupMarkers, number: Int?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val badgeStyle = TextStyle(
        color = DefaultExtendedColors.alert.content,
        fontSize = BADGE_TEXT_SIZE,
        fontWeight = FontWeight.Bold,
    )
    Box(
        modifier
            // Takes every touch, so the content stays static (no ripple, no star change, no menu).
            .pointerInput(Unit) {}
            .onGloballyPositioned { holder[0] = it }
            .drawBehind {
                val overlay = holder[0] ?: return@drawBehind
                if (!overlay.isAttached) return@drawBehind
                val spots = markers.spots.values
                    .filter { it.isAttached }
                    .map { overlay.localBoundingBoxOf(it, clipBounds = false) }
                    .sortedWith(compareBy({ it.top }, { it.left }))
                spots.forEach { drawRing(it) }
                val first = spots.firstOrNull()
                if (number != null && first != null) {
                    drawBadge(first, number.toString(), measurer, badgeStyle)
                }
            },
    )
}

private fun DrawScope.drawRing(spot: Rect) {
    val gap = RING_GAP.toPx()
    // Kept inside the frame, so a spot at the screen's edge does not draw over the outline.
    val inset = RING_STROKE.toPx()
    val ring = spot.inflate(gap).intersect(Rect(inset, inset, size.width - inset, size.height - inset))
    val radius = minOf(RING_CORNER.toPx(), ring.height / 2)
    drawRoundRect(
        color = MATCHA,
        topLeft = ring.topLeft,
        size = ring.size,
        cornerRadius = CornerRadius(radius),
        style = Stroke(RING_STROKE.toPx()),
    )
}

private fun DrawScope.drawBadge(spot: Rect, text: String, measurer: TextMeasurer, style: TextStyle) {
    val gap = RING_GAP.toPx()
    val radius = BADGE_SIZE.toPx() / 2
    // On the ring's top-left corner, kept inside the frame.
    val center = Offset(
        x = (spot.left - gap).coerceIn(radius, size.width - radius),
        y = (spot.top - gap).coerceIn(radius, size.height - radius),
    )
    drawCircle(MATCHA, radius, center)
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f),
    )
}

private val MATCHA = DefaultExtendedColors.alert.matcha
private val FRAME_CORNER = 12.dp
private val RING_GAP = 2.dp
private val RING_STROKE = 2.dp
private val RING_CORNER = 8.dp
private val BADGE_SIZE = 18.dp
private val BADGE_TEXT_SIZE = 11.sp
