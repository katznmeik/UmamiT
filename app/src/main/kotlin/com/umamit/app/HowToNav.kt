package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.umamit.feature.onboarding.howto.HowToListScreen
import com.umamit.feature.onboarding.howto.HowToTopic
import com.umamit.feature.onboarding.howto.HowToTopicScreen
import kotlinx.serialization.Serializable

/**
 * The list of the eight Anleitungen (SCR-35) from *Einstellungen* › *Hilfe* › *Anleitungen* and *Meine Tees* ⋮ *Hilfe*
 * (SCR-36 „Entry points", D-137 Q10): full-screen above the shell or the timer like [TourRoute], so back returns to
 * the screen it was opened from.
 */
@Serializable
internal data object HowToListRoute

/** One Anleitung (SCR-36): [topic] is the [HowToTopic]'s index, 0 to 7 (the topic numbers 1 to 8 less one). */
@Serializable
internal data class HowToTopicRoute(val topic: Int)

/**
 * Opens the Anleitungen above the shell or the timer (they give way to an alert, rule 8): [openList] the list,
 * [openTopic] one topic. Provided to the whole root by [LocalHowTo], so each screen's ⋮ *Hilfe* is wired where the
 * screen is built, without a parameter through every layer. Nothing opens in a test or preview that provides none.
 */
internal class HowToOpener(val openList: () -> Unit = {}, val openTopic: (HowToTopic) -> Unit = {}) {
    /** The action of a ⋮ *Hilfe* that opens [topic]. */
    fun topic(topic: HowToTopic): () -> Unit = { openTopic(topic) }
}

/** The Anleitungen's opener for the screens below the root. */
@Suppress("CompositionLocalAllowlist") // one root-wide service, so each screen's ⋮ *Hilfe* needs no host plumbing
internal val LocalHowTo = staticCompositionLocalOf { HowToOpener() }

/** The root's [HowToOpener]: both routes open above what is shown through [navigateAbove], once (single top). */
@Composable
internal fun rememberHowToOpener(
    navigateAbove: (route: Any, options: NavOptionsBuilder.() -> Unit) -> Unit,
): HowToOpener {
    val current by rememberUpdatedState(navigateAbove)
    return remember {
        HowToOpener(
            openList = { current(HowToListRoute) { launchSingleTop = true } },
            openTopic = { topic -> current(HowToTopicRoute(topic.ordinal)) { launchSingleTop = true } },
        )
    }
}

/**
 * Registers the Anleitungen: the list, whose rows open a topic above it, and a topic. Back pops one screen, only while
 * it is resumed (D-090), so it returns to the screen the help was opened from, or to the list.
 */
internal fun NavGraphBuilder.howToRoutes(navController: NavHostController, howTo: HowToOpener) {
    composable<HowToListRoute> {
        HowToListScreen(
            onOpenTopic = resumedOnly<HowToTopic> { howTo.openTopic(it) },
            onBack = dropUnlessResumed { navController.popBackStack() },
        )
    }
    composable<HowToTopicRoute> { entry ->
        HowToTopicScreen(
            topic = HowToTopic.entries[entry.toRoute<HowToTopicRoute>().topic],
            onBack = dropUnlessResumed { navController.popBackStack() },
        )
    }
}
