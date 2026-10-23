package com.umamit.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.umamit.core.designsystem.R

/**
 * The icons M1 uses (03 §3.4), as vector drawables for `painterResource`:
 * Material Symbols Outlined, weight 300 (Apache 2.0), and placeholder line
 * icons (own work) for the custom set until the PO's icons exist.
 */
object UmamiIcons {
    // Navigation (03 §2.2)
    @DrawableRes val Start = R.drawable.ic_home

    @DrawableRes val Journal = R.drawable.ic_menu_book

    @DrawableRes val Collection = R.drawable.ic_eco

    @DrawableRes val Statistics = R.drawable.ic_bar_chart

    // Actions
    @DrawableRes val Settings = R.drawable.ic_settings

    @DrawableRes val MoreVert = R.drawable.ic_more_vert

    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back

    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right

    @DrawableRes val Pause = R.drawable.ic_pause

    @DrawableRes val Play = R.drawable.ic_play_arrow

    @DrawableRes val Add = R.drawable.ic_add

    @DrawableRes val Remove = R.drawable.ic_remove

    @DrawableRes val Delete = R.drawable.ic_delete

    @DrawableRes val Check = R.drawable.ic_check

    @DrawableRes val Close = R.drawable.ic_close

    @DrawableRes val Edit = R.drawable.ic_edit

    @DrawableRes val Schedule = R.drawable.ic_schedule

    @DrawableRes val Timer = R.drawable.ic_timer

    @DrawableRes val Warning = R.drawable.ic_warning

    @DrawableRes val StarFilled = R.drawable.ic_star_filled

    // Custom line icons (placeholders, 03 §3.4)
    @DrawableRes val Leaf = R.drawable.ic_custom_leaf

    @DrawableRes val Kettle = R.drawable.ic_custom_kettle

    @DrawableRes val Thermometer = R.drawable.ic_custom_thermometer

    @DrawableRes val Infusions = R.drawable.ic_custom_infusions

    // Illustrations (placeholders, 03 §3.5)
    @DrawableRes val LandscapePlaceholder = R.drawable.illustration_placeholder_landscape

    // Photos (03 §3.5, docs/design/assets.md)

    /** A4: the timer's photo for a tea without one of its own (SCR-05). */
    @DrawableRes val TimerFallbackPhoto = R.drawable.photo_timer_fallback
}
