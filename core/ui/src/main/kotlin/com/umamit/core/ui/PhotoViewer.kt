package com.umamit.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.umamit.core.designsystem.component.OverPhoto
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.geometry.lerp as lerpOffset

/** The viewer's pager. */
const val PHOTO_VIEWER_PAGER_TAG = "photo_viewer_pager"

/** The viewer's „2 / 5" counter. */
const val PHOTO_VIEWER_COUNTER_TAG = "photo_viewer_counter"

/** The viewer's ←. */
const val PHOTO_VIEWER_BACK_TAG = "photo_viewer_back"

/** The photo of a page, for tests. */
const val PHOTO_VIEWER_PHOTO_TAG = "photo_viewer_photo"

/**
 * The full-screen photo viewer (SCR-28, TEA-05, D-114 Q9): [sources] on black, each fitted whole (never cropped),
 * pinch and double-tap zoom up to 4× (double-tap back to fit), a swipe to the neighbouring photo (not while zoomed
 * in), „2 / 5" at the top with ← on a 40 dp translucent circle. No actions. [onPageChange] reports the page shown,
 * so the hero can follow when the viewer closes. TalkBack reads each photo as „Foto 2 von 5".
 */
@Composable
fun PhotoViewerScreen(
    sources: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
    photo: @Composable (source: String) -> Painter? = { rememberViewerPainter(it) },
) {
    val count = sources.size
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    var zoomed by remember { mutableStateOf(false) }
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { currentOnPageChange(it) } }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            key = { sources.getOrNull(it) ?: it },
            modifier = Modifier.fillMaxSize().testTag(PHOTO_VIEWER_PAGER_TAG),
        ) { page ->
            ZoomablePhoto(
                painter = photo(sources[page]),
                description = stringResource(R.string.ui_photo_viewer_description, page + 1, count),
                onZoomChange = { if (pager.currentPage == page) zoomed = it },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(UmamiSpacing.XSmall),
        ) {
            OverPhoto(Modifier.align(Alignment.CenterStart).size(BACK_SIZE)) {
                IconButton(onClick = onBack, modifier = Modifier.size(BACK_SIZE).testTag(PHOTO_VIEWER_BACK_TAG)) {
                    Icon(
                        painterResource(UmamiIcons.ArrowBack),
                        contentDescription = stringResource(com.umamit.core.designsystem.R.string.ds_back),
                    )
                }
            }
            if (count > 0) {
                Text(
                    text = stringResource(R.string.ui_photo_viewer_counter, pager.currentPage + 1, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    // TalkBack reads each photo („Foto 2 von 5"), not this as well.
                    modifier = Modifier
                        .align(Alignment.Center)
                        .semantics { hideFromAccessibility() }
                        .testTag(PHOTO_VIEWER_COUNTER_TAG),
                )
            }
        }
    }
}

/**
 * One photo that zooms (≤ [MAX_ZOOM]×) by pinch and double-tap and pans while zoomed. At fit a one-finger swipe is
 * left to the pager; zoomed in it pans the photo, and [onZoomChange] tells the pager to stay still.
 */
@Composable
private fun ZoomablePhoto(painter: Painter?, description: String, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val spec = UmamiTheme.motion.standard<Float>()
    val currentPainter by rememberUpdatedState(painter)
    val currentOnZoomed by rememberUpdatedState(onZoomChange)
    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { currentOnZoomed(zoomed) }
    DisposableEffect(Unit) { onDispose { currentOnZoomed(false) } }

    fun clamp(candidate: Offset, atScale: Float): Offset {
        val fitted = currentPainter?.intrinsicSize.fittedIn(box)
        val maxX = max(0f, (fitted.width * atScale - box.width) / 2f)
        val maxY = max(0f, (fitted.height * atScale - box.height) / 2f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val fromScale = scale
                        val fromOffset = offset
                        val toScale = if (fromScale > 1f) 1f else DOUBLE_TAP_ZOOM
                        val center = Offset(box.width / 2f, box.height / 2f)
                        val toOffset = clamp((center - tap) * (toScale - 1f), toScale)
                        scope.launch {
                            animate(0f, 1f, animationSpec = spec) { t, _ ->
                                scale = lerp(fromScale, toScale, t)
                                offset =
                                    if (toScale == 1f) fromOffset * (1f - t) else lerpOffset(fromOffset, toOffset, t)
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectZoomAndPan(isZoomed = { scale > 1f }) { centroid, pan, zoom ->
                    val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    val point = centroid - Offset(box.width / 2f, box.height / 2f)
                    // The point under the fingers stays under them while the scale changes.
                    val moved = point - (point - offset) * (next / scale) + pan
                    scale = next
                    offset = if (next == 1f) Offset.Zero else clamp(moved, next)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .testTag(PHOTO_VIEWER_PHOTO_TAG),
            )
        }
    }
}

/** The size [this] natural size takes when fitted whole into [box]; the box itself when it is not known. */
private fun Size?.fittedIn(box: IntSize): Size {
    val whole = Size(box.width.toFloat(), box.height.toFloat())
    val natural = this?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f } ?: return whole
    val fit = min(box.width / natural.width, box.height / natural.height)
    return Size(natural.width * fit, natural.height * fit)
}

/**
 * Pinch and pan that leave a one-finger drag alone while the photo is not zoomed in ([isZoomed] false), so the pager
 * swipes; with two fingers, or zoomed in, the movement is consumed and handed to [onGesture] (centroid, pan, zoom).
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastSlop = false
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val consumed = event.changes.any { it.isConsumed }
            if (!consumed) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val size = event.calculateCentroidSize(useCurrent = false)
                    pastSlop = movedPastSlop(zoom, pan, size, viewConfiguration.touchSlop)
                }
                val twoFingers = event.changes.count { it.pressed } > 1
                if (pastSlop && (twoFingers || isZoomed())) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
                    }
                    event.changes.consumeMoves()
                }
            }
        } while (!consumed && event.changes.any { it.pressed && !it.changedToUp() })
    }
}

/** Whether the fingers have moved apart or together ([zoom] of [centroidSize]) or along ([pan]) beyond [slop]. */
private fun movedPastSlop(zoom: Float, pan: Offset, centroidSize: Float, slop: Float): Boolean =
    abs(1f - zoom) * centroidSize > slop || pan.getDistance() > slop

private fun List<PointerInputChange>.consumeMoves() = forEach { if (it.positionChanged()) it.consume() }

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val BACK_SIZE = 40.dp
