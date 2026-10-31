package com.umamit.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A − / + of the Schritte-Sheet that repeats while held (SCR-31, #141): a tap is one step; held, it repeats on
 * [HoldRepeat]'s schedule, each repeat one step with a light haptic tick, and a release (or the sheet leaving,
 * e.g. for an alert) stops it at once. It looks and measures like Material's `IconButton` (40 dp, a 48 dp
 * target). [onStep] applies one step and returns whether it changed anything; at a bound the hold stops.
 * TalkBack gets a plain button: one step per activation, no repeat. Shared in `core:ui` (D-100) for every
 * − / + stepper that repeats while held.
 */
@Composable
fun RepeatingIconButton(
    icon: Painter,
    contentDescription: String,
    enabled: Boolean,
    onStep: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnStep by rememberUpdatedState(onStep)
    val interactions = remember { MutableInteractionSource() }
    val contentColor = LocalContentColor.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ButtonSize)
            .clip(CircleShape)
            .indication(interactions, ripple())
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        if (!currentEnabled) return@detectTapGestures
                        val press = PressInteraction.Press(offset)
                        interactions.emit(press)
                        var repeats = 0
                        val released = coroutineScope {
                            val repeater = launch {
                                var heldMs = 0L
                                for (atMs in HoldRepeat.timesMs()) {
                                    delay(atMs - heldMs)
                                    heldMs = atMs
                                    // A hold that reached a bound ends there, without a tick.
                                    if (!currentOnStep()) break
                                    repeats++
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                            tryAwaitRelease().also { repeater.cancel() }
                        }
                        interactions.emit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                        )
                        // A plain tap is one step; a hold already stepped (a scroll that took the touch is none).
                        if (released && repeats == 0) currentOnStep()
                    },
                )
            }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick {
                    if (currentEnabled) currentOnStep()
                    currentEnabled
                }
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            // Material's disabled icon button: the content colour at 38 %.
            tint = if (enabled) contentColor else contentColor.copy(alpha = DISABLED_ALPHA),
        )
    }
}

private val ButtonSize = 40.dp
private const val DISABLED_ALPHA = 0.38f
