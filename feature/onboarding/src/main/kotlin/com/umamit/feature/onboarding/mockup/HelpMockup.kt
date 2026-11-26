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
 * the scaled content; with [markerNumber] (an Anleitung's step) the first ring gets a number badge beside it,
 * outside the ring.
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
 * number badge goes beside the topmost. [badgeBelow] puts the badge below a spot as wide as the screen instead of
 * above it, where the row above has text at its end.
 */
fun Modifier.mockupMarker(badgeBelow: Boolean = false): Modifier = this then MockupMarkerElement(badgeBelow)

private data class MockupMarkerElement(val badgeBelow: Boolean) : ModifierNodeElement<MockupMarkerNode>() {
    override fun create() = MockupMarkerNode(badgeBelow)

    override fun update(node: MockupMarkerNode) {
        node.badgeBelow = badgeBelow
    }
}

/** Reports its spot's coordinates to the enclosing [HelpMockup] (through [LocalMockupMarkers]). */
private class MockupMarkerNode(var badgeBelow: Boolean) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var markers: MockupMarkers? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val target = currentValueOf(LocalMockupMarkers) ?: return
        markers = target
        val spot = target.spots[this]
        if (spot?.coordinates !== coordinates || spot.badgeBelow != badgeBelow) {
            target.spots[this] = MarkerSpot(coordinates, badgeBelow)
        }
    }

    override fun onDetach() {
        markers?.spots?.remove(this)
        markers = null
    }
}

/** The spots reported by the content. */
internal class MockupMarkers {
    val spots = mutableStateMapOf<Any, MarkerSpot>()
}

/** A marked spot and where its badge prefers to go. */
internal class MarkerSpot(val coordinates: LayoutCoordinates, val badgeBelow: Boolean)

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
                    .filter { it.coordinates.isAttached }
                    .map { overlay.localBoundingBoxOf(it.coordinates, clipBounds = false) to it.badgeBelow }
                    .sortedWith(compareBy({ it.first.top }, { it.first.left }))
                spots.forEach { drawRing(it.first) }
                val first = spots.firstOrNull()
                if (number != null && first != null) {
                    drawBadge(first.first, first.second, number.toString(), measurer, badgeStyle)
                }
            },
    )
}

private fun DrawScope.drawRing(spot: Rect) {
    val ring = ringRect(spot)
    val radius = minOf(RING_CORNER.toPx(), ring.height / 2)
    drawRoundRect(
        color = MATCHA,
        topLeft = ring.topLeft,
        size = ring.size,
        cornerRadius = CornerRadius(radius),
        style = Stroke(RING_STROKE.toPx()),
    )
}

/** The ring around [spot]: [RING_GAP] outside it, kept inside the frame so it does not draw over the outline. */
private fun DrawScope.ringRect(spot: Rect): Rect {
    val inset = RING_STROKE.toPx()
    return spot.inflate(RING_GAP.toPx()).intersect(Rect(inset, inset, size.width - inset, size.height - inset))
}

private fun DrawScope.drawBadge(spot: Rect, below: Boolean, text: String, measurer: TextMeasurer, style: TextStyle) {
    val radius = BADGE_SIZE.toPx() / 2
    val center = badgeCenter(ringRect(spot), radius, below)
    drawCircle(MATCHA, radius, center)
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f),
    )
}

/**
 * Where the step badge goes (SCR-36, D-137): outside the [ring], so it covers no text. Left of the ring at its
 * vertical centre; where there is no room on the left (the spot at the frame's left edge), right of it, else above
 * its right end (a spot as wide as the screen; text, set from the start, ends first there), else below it; [below]
 * (or no room above) takes below first. Always kept inside the frame.
 */
private fun DrawScope.badgeCenter(ring: Rect, radius: Float, below: Boolean): Offset {
    val offset = radius + BADGE_GAP.toPx() + RING_STROKE.toPx() / 2
    val edge = RING_STROKE.toPx() + radius
    val center = when {
        ring.left - offset >= edge -> Offset(ring.left - offset, ring.center.y)
        ring.right + offset <= size.width - edge -> Offset(ring.right + offset, ring.center.y)
        !below && ring.top - offset >= edge -> Offset(ring.right - radius, ring.top - offset)
        else -> Offset(ring.right - radius, ring.bottom + offset)
    }
    return Offset(
        x = center.x.coerceIn(edge, size.width - edge),
        y = center.y.coerceIn(edge, size.height - edge),
    )
}

private val MATCHA = DefaultExtendedColors.alert.matcha
private val FRAME_CORNER = 12.dp
private val RING_GAP = 2.dp
private val RING_STROKE = 2.dp
private val RING_CORNER = 8.dp
private val BADGE_SIZE = 18.dp
private val BADGE_GAP = 2.dp
private val BADGE_TEXT_SIZE = 11.sp
