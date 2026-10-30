package com.umamit.feature.brew.timer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/** Test tag of the ink-wash band at the top of the timer (SCR-05, SCR-07, D-095). */
const val TIMER_BAND_TAG = "timer_band"

/** Test tag of the brew's tea seal (SCR-05, D-095). */
const val TIMER_SEAL_TAG = "timer_seal"

/**
 * The band: full width, about 220 dp, from the top of the screen behind the status bar (D-095);
 * shorter on a short screen ([TimerBandPage], D-096).
 */
internal val TimerBandHeight = 220.dp

/** The seal's washi disc. */
internal val TimerSealSize = 88.dp

/** The thin ring, inset into the disc, and the photo's circle inside it. */
private val SealRingInset = 3.dp
private val SealRingWidth = 1.dp
private val SealPhotoInset = 6.dp
private const val SEAL_RING_ALPHA = 0.3f

/** The leaf takes half of the photo's circle, as in a tea thumbnail (03 §3.5). */
private const val SEAL_LEAF_FRACTION = 0.5f

/** Where the band starts to fade into the background, as the tea photo did until v0.2.0. */
private const val BAND_FADE_START = 0.35f

/**
 * What the brew's seal shows (SCR-05, D-095): the session tea's [styleKey] for
 * the leaf's colour and the file of its cover photo, null without one.
 */
@Immutable
data class TimerTeaSeal(val styleKey: String?, val photoPath: String?)

/** Test seam: what the seal draws. */
internal enum class SealContent { Photo, Leaf }

internal val SealContentKey = SemanticsPropertyKey<SealContent>("SealContent")

/**
 * The ink-wash band A13 at the top, centre-cropped and fading into the
 * Steeping background (D-095). Decorative: the title names the tea.
 */
@Composable
internal fun TimerBand(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Image(
        painter = painterResource(UmamiIcons.TimerBand),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(TimerBandHeight)
            .testTag(TIMER_BAND_TAG)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(BAND_FADE_START to Color.Transparent, 1f to background))
            },
    )
}

/**
 * The timer's scrolling page under the ink-wash band (SCR-05, SCR-07, D-095):
 * the [band] from the top of the screen, behind the status bar; ← and ⋮
 * ([topBar]) below the status bar; for a brew ([withSeal]) the [seal] centred
 * on the band's lower edge; then [aboveControls], from the title down to the
 * space above the primary control, [controls], the primary control (and,
 * with [fitControls], the secondary control below it), and [afterControls],
 * what follows them (a banner), all centred. The quick timer has no seal; its content starts at
 * the band's lower edge. The band's and the seal's space stays when they draw
 * nothing, as on the alert surface (D-058).
 *
 * **Short screens (rule 8, D-096).** With [adaptive], the band is
 * [TimerBandHeight] tall when everything down to the primary control fits,
 * and gives way otherwise, down to the status bar and the top bar, until the
 * primary control ([controlHeight] tall) and the bottom margin lie on the
 * screen without scrolling. Its height follows only the screen, the insets
 * and [aboveControls], never [controls] or whether the band and the seal are
 * drawn, so nothing moves when the alert starts. With [fitControls] (FINISHED,
 * whose *Weiterer Aufguss* sits below *Sitzung beenden*) the measured
 * [controls] take [controlHeight]'s place, so the secondary control and the
 * bottom margin fit too. What does not fit even then scrolls, and so does
 * [afterControls].
 */
@Composable
internal fun TimerBandPage(
    withSeal: Boolean,
    controlHeight: Dp,
    band: @Composable () -> Unit,
    topBar: @Composable BoxScope.() -> Unit,
    seal: @Composable () -> Unit,
    aboveControls: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    adaptive: Boolean = withSeal,
    fitControls: Boolean = false,
    afterControls: @Composable ColumnScope.() -> Unit = {},
) {
    val insets = WindowInsets.safeDrawing
    val top: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            topBar()
            if (withSeal) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).size(TimerSealSize)) { seal() }
            }
        }
    }
    val column: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = aboveControls)
    }
    val below: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = controls)
    }
    val after: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = afterControls)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight
        Layout(
            contents = listOf(band, top, column, below, after),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) { slots, constraints ->
            val bandSlot = slots[0]
            val topSlot = slots[1]
            val contentSlot = slots[2]
            val controlsSlot = slots[3]
            val afterSlot = slots[4]
            val statusBar = insets.getTop(this)
            val left = insets.getLeft(this, layoutDirection)
            val width = constraints.maxWidth
            val columnWidth = (width - left - insets.getRight(this, layoutDirection)).coerceAtLeast(0)
            val inColumn = Constraints.fixedWidth(columnWidth)
            val contentPlaceable = contentSlot.single().measure(inColumn)
            val controlsPlaceable = controlsSlot.single().measure(inColumn)
            val afterPlaceable = afterSlot.single().measure(inColumn)

            val sealHalf = if (withSeal) TimerSealSize.roundToPx() / 2 else 0
            val margin = UmamiSpacing.Large.roundToPx()
            val tallest = TimerBandHeight.roundToPx()
            val bandHeight = if (adaptive && screenHeight != Constraints.Infinity) {
                val lowest = minOf(statusBar + TimerTopBarHeight.roundToPx(), tallest)
                // The band's lower edge as low as the content, the primary control and the margin allow.
                val fitted = if (fitControls) controlsPlaceable.height else controlHeight.roundToPx()
                val fits = screenHeight - insets.getBottom(this) - margin - fitted -
                    contentPlaceable.height - sealHalf
                fits.coerceIn(lowest, tallest)
            } else {
                tallest
            }
            val topHeight = (bandHeight + sealHalf - statusBar).coerceAtLeast(0)
            val topPlaceable = topSlot.single().measure(Constraints.fixed(columnWidth, topHeight))
            val bandPlaceables = bandSlot.map { it.measure(Constraints.fixed(width, bandHeight)) }

            val contentTop = statusBar + topHeight
            val controlsTop = contentTop + contentPlaceable.height
            val afterTop = controlsTop + controlsPlaceable.height
            val height = afterTop + afterPlaceable.height + margin + insets.getBottom(this)
            layout(width, height.coerceAtLeast(constraints.minHeight)) {
                bandPlaceables.forEach { it.place(0, 0) }
                topPlaceable.place(left, statusBar)
                contentPlaceable.place(left, contentTop)
                controlsPlaceable.place(left, controlsTop)
                afterPlaceable.place(left, afterTop)
            }
        }
    }
}

/** ← and ⋮ with their 48 dp touch targets: the band never gets shorter than the status bar and these. */
private val TimerTopBarHeight = UmamiSpacing.MinTouchTarget

/**
 * The tea seal (D-095): an 88 dp washi disc with a thin ring; the tea's
 * [photo] fills the inner circle, drawn with a multiply blend so a white
 * background turns into the disc's cream. Without a photo, the leaf in the
 * colour of the tea's [styleKey]. Decorative: the title names the tea.
 */
@Composable
internal fun TeaSeal(photo: Painter?, styleKey: String?, modifier: Modifier = Modifier) {
    val leaf = rememberSealLeafPainter(styleKey)
    val content = photo ?: leaf
    val washi = UmamiTheme.extendedColors.alert.washi
    val ring = UmamiTheme.extendedColors.alert.content.copy(alpha = SEAL_RING_ALPHA)
    Spacer(
        modifier = modifier
            .size(TimerSealSize)
            .testTag(TIMER_SEAL_TAG)
            .semantics { this[SealContentKey] = if (photo != null) SealContent.Photo else SealContent.Leaf }
            .drawBehind {
                val radius = size.minDimension / 2
                drawCircle(washi, radius)
                drawCircle(ring, radius - SealRingInset.toPx(), style = Stroke(SealRingWidth.toPx()))
                drawMultiplied(content, Rect(center, radius - SealPhotoInset.toPx()))
            },
    )
}

/**
 * Draws [painter] centre-cropped into the circle of [bounds], multiplied onto
 * what is below: white becomes the disc's cream, the leaves keep their colour.
 * One draw pass on the disc's own canvas, so the blend sees the disc.
 */
private fun DrawScope.drawMultiplied(painter: Painter, bounds: Rect) {
    val circle = Path().apply { addOval(bounds) }
    clipPath(circle) {
        val target = cropTo(painter.intrinsicSize, bounds)
        drawContext.canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.Multiply })
        translate(target.left, target.top) {
            with(painter) { draw(target.size) }
        }
        drawContext.canvas.restore()
    }
}

/** [ContentScale.Crop] by hand: [source] scaled to cover [bounds], centred; unsized painters fill it. */
private fun cropTo(source: Size, bounds: Rect): Rect {
    if (source == Size.Unspecified || source.width <= 0f || source.height <= 0f) return bounds
    val scale = maxOf(bounds.width / source.width, bounds.height / source.height)
    val size = Size(source.width * scale, source.height * scale)
    return Rect(
        left = bounds.center.x - size.width / 2,
        top = bounds.center.y - size.height / 2,
        right = bounds.center.x + size.width / 2,
        bottom = bounds.center.y + size.height / 2,
    )
}

/** The seal's leaf in the tea type's colour (03 §3.5), as a painter, also while a photo is read. */
@Composable
internal fun rememberSealLeafPainter(styleKey: String?): Painter {
    val leaf = painterResource(UmamiIcons.Leaf)
    val color = UmamiTheme.extendedColors.teaTypes.forStyle(styleKey)
    return remember(leaf, color) { SealLeafPainter(leaf, color) }
}

private class SealLeafPainter(private val leaf: Painter, color: Color) : Painter() {
    private val tint = ColorFilter.tint(color)

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val side = size.minDimension * SEAL_LEAF_FRACTION
        translate((size.width - side) / 2, (size.height - side) / 2) {
            with(leaf) { draw(Size(side, side), colorFilter = tint) }
        }
    }
}
