package com.umamit.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.umamit.core.designsystem.icon.UmamiIcons
import kotlinx.serialization.Serializable

/** The navigation shell with its four tabs: the root of the app's top-level navigation. */
@Serializable
internal data object ShellRoute

/** The timer (SCR-05): full-screen, above the shell, without the navigation bar and the active brew bar (03 §2.2). */
@Serializable
internal data object TimerRoute

/** *Timer-Einstellungen* from the timer's overflow; on the timer's stack, so back returns to the timer. */
@Serializable
internal data object TimerSettingsRoute

/** Type-safe routes (06 §5.5). Every tab owns a navigation controller, so each has its own back stack. */
@Serializable
internal data object StartRoute

@Serializable
internal data object JournalRoute

@Serializable
internal data object CollectionRoute

@Serializable
internal data object StatsRoute

@Serializable
internal data object SettingsRoute

/** Throwaway probe screen (M1-03a), reached from the settings until it is removed before v0.1.0. */
@Serializable
internal data object ProbeRoute

/** The four areas of 03 §2.2, in navigation order. */
internal enum class Tab(val root: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Start(StartRoute, R.string.tab_start, UmamiIcons.Start),
    Journal(JournalRoute, R.string.tab_journal, UmamiIcons.Journal),
    Collection(CollectionRoute, R.string.tab_collection, UmamiIcons.Collection),
    Stats(StatsRoute, R.string.tab_stats, UmamiIcons.Statistics),
}
