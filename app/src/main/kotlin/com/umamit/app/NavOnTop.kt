package com.umamit.app

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/**
 * Whether [entry] is the top of this back stack (D-090, D-125): a screen's outcome acts only while its own entry is
 * on top, so a quick double tap or a late outcome never pops another screen.
 */
internal fun NavHostController.isOnTop(entry: NavBackStackEntry): Boolean = currentBackStackEntry?.id == entry.id

/** Pops the back stack only while [entry] is on top (D-090, D-125); otherwise does nothing. */
internal fun NavHostController.popIfOnTop(entry: NavBackStackEntry) {
    if (isOnTop(entry)) popBackStack()
}
