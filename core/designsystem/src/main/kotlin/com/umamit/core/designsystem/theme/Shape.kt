package com.umamit.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shapes (03 §3.3): cards 16 dp, the primary action card 12 dp, pills fully
 * rounded, round thumbnails. Flat design: hairlines instead of shadows.
 */
val UmamiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Named shapes of 03 §3.3 for the components. */
object UmamiShape {
    val Card = RoundedCornerShape(16.dp)
    val PrimaryCard = RoundedCornerShape(12.dp)
    val Pill = CircleShape
    val Thumbnail = CircleShape
}

/** Spacing scale 4/8/12/16/24/32/48 dp, side margin 20 dp, hairline 1 dp (03 §3.3). */
object UmamiSpacing {
    val XXSmall = 4.dp
    val XSmall = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
    val XLarge = 32.dp
    val XXLarge = 48.dp

    /** Horizontal screen margin. */
    val SideMargin = 20.dp

    /** Hairline dividers and outlines. */
    val Hairline = 1.dp

    /** Minimum touch target (03 §6). */
    val MinTouchTarget = 48.dp
}
