package com.umamit.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.model.TimerSlot
import kotlinx.serialization.Serializable

/** The navigation shell with its four tabs: the root of the app's top-level navigation. */
@Serializable
internal data object ShellRoute

/**
 * The timer (SCR-05; SCR-07 once the quick timer runs): full-screen, above the shell, without the navigation bar and
 * the active brew bar (03 §2.2). It shows [slot]'s timer, or, for none, the one in front when it opened (D-098).
 * [switched]: opened from the other timer's row, which is right below it, so that row's tap there goes back.
 */
@Serializable
internal data class TimerRoute(val slotName: String?, val switched: Boolean) {
    /** The timer this route shows; null: the one in front when it opened. By name: a route holds no enum (R8). */
    val slot: TimerSlot? get() = slotName?.let(TimerSlot::valueOf)

    companion object {
        operator fun invoke(slot: TimerSlot? = null, switched: Boolean = false) = TimerRoute(slot?.name, switched)
    }
}

/**
 * The setup (SCR-04): of the tea [teaId] (from the tea picker or a favourite), or of the waiting
 * session [sessionId] (*Brühen anpassen* on the timer, BRW-11). Full-screen like the timer. [recipeId]: the recipe
 * tapped on the tea detail (SCR-09); null opens the tea's default recipe. [pastSessionId]: a past session's *So
 * nochmal brühen* (SCR-16, BRW-06), the setup holding that session as brewed.
 */
@Serializable
internal data class BrewSetupRoute(
    val teaId: String? = null,
    val sessionId: String? = null,
    val recipeId: String? = null,
    val pastSessionId: String? = null,
)

/**
 * A past session's page (SCR-16, read-only in v0.4.0) in the Journal tab's stack; [sessionId] is named as its view
 * model reads it.
 */
@Serializable
internal data class JournalSessionRoute(val sessionId: String)

/**
 * A catalog tea's detail (SCR-21) from the setup's ⋮ *Im Katalog ansehen* (D-098): full-screen above the setup,
 * whose back-stack entry keeps its draft, so back returns to it unchanged. [key] is the catalog key (05 §4.1), named
 * as the detail's view model reads it. It gives way to a new alert like every route above the shell (rule 8).
 */
@Serializable
internal data class SetupCatalogTeaRoute(val key: String)

/**
 * A collection tea's detail (SCR-09) from the setup's ⋮ *Tee ansehen* (D-100): full-screen above the setup, whose
 * back-stack entry keeps its draft, so back returns to it unchanged. [teaId] is named as the detail's view model
 * reads it. It gives way to a new alert like every route above the shell (rule 8).
 */
@Serializable
internal data class SetupTeaRoute(val teaId: String)

/**
 * The quick timer before its *Start* (SCR-07), from Start and the tea picker. Full-screen like the setup;
 * once it runs, the timer replaces it.
 */
@Serializable
internal data object QuickTimerRoute

/** *Timer-Einstellungen* from the timer's overflow; on the timer's stack, so back returns to the timer. */
@Serializable
internal data object TimerSettingsRoute

/**
 * Sammlung › Katalog with the refresh sheet, opened by a tap on a catalog refresh notification
 * (SCR-22, 03 §2.4, D-097): a shell of its own above whatever was shown, the timer included, so
 * back returns there. It gives way to a new alert like every route above the shell (rule 8).
 * There is at most one; it keeps the [sheetToken] of the tap it shows, so it never takes up a
 * later tap's (#121).
 */
@Serializable
internal data class CatalogRefreshRoute(val sheetToken: Long)

/** Type-safe routes (06 §5.5). Every tab owns a navigation controller, so each has its own back stack. */
@Serializable
internal data object StartRoute

@Serializable
internal data object JournalRoute

@Serializable
internal data object CollectionRoute

@Serializable
internal data object StatsRoute

/** A catalog tea's detail (SCR-21), in the Sammlung tab's stack; [key] is the catalog key (05 §4.1). */
@Serializable
internal data class CatalogTeaRoute(val key: String)

/** A collection tea's detail (SCR-09), in the Sammlung tab's stack; [teaId] is named as its view model reads it. */
@Serializable
internal data class CollectionTeaRoute(val teaId: String)

/** *Sorte wählen* (SCR-11) from *Meine Tees*' *Ohne Katalog* (TEA-01, D-100), in the Sammlung tab's stack. */
@Serializable
internal data object CollectionSorteRoute

/**
 * Tee bearbeiten (SCR-10), in the Sammlung tab's stack: the tea [teaId] from its detail's pencil, or with none a new
 * tea of the Sorte [styleKey] chosen in *Sorte wählen* (`other`: *Andere / unbekannt*). Named as the editor's view
 * model reads them (`TeaEditorViewModel.TEA_ID`, `STYLE_KEY`).
 */
@Serializable
internal data class CollectionTeaEditorRoute(val teaId: String? = null, val styleKey: String? = null)

/**
 * Rezept bearbeiten (SCR-12), in the Sammlung tab's stack: the recipe [recipeId] of the tea [teaId] from a pencil in
 * its detail's REZEPTE, or with none *Rezept hinzufügen* (PRF-01, D-100). Named as the editor's view model reads
 * them (`RecipeEditorViewModel.TEA_ID`, `RECIPE_ID`).
 */
@Serializable
internal data class CollectionRecipeEditorRoute(val teaId: String, val recipeId: String? = null)

/** The *Ratgeber* list (CAT-09), in the Sammlung tab's stack. */
@Serializable
internal data object GuidesRoute

/** A guide's article (SCR-20), in the Sammlung tab's stack; [slug] is the guide's key (05 §4.3). */
@Serializable
internal data class GuideRoute(val slug: String)

@Serializable
internal data object SettingsRoute

/** *Über* (SCR-27): from the settings, in a tab's stack or above the timer (*Timer-Einstellungen*). */
@Serializable
internal data object AboutRoute

/**
 * The first-start setup (SCR-01): the start destination until it is completed (ONB-04), and
 * reached again from *Über › Einrichtung erneut zeigen* above the shell.
 */
@Serializable
internal data object OnboardingRoute

/** *Diagnose* (SCR-27, M1-20), under *Über*. */
@Serializable
internal data object DiagnosticsRoute

/** *Teegeschirr* (SCR-13) from *Einstellungen › Teegeschirr* (SET-30, D-100): in a tab's stack or above the timer. */
@Serializable
internal data object TeawareRoute

/**
 * The vessel editor (SCR-14): [vesselId] to edit, null for a new vessel (named as the editor's view model reads it,
 * `VesselEditorViewModel.VESSEL_ID`). It closes with its result in the entry below ([closeVesselEditor]): the saved
 * vessel's id, for the setup's *Neues Gefäß …* (WARE-02), or the archived vessel, for the list's snackbar.
 */
@Serializable
internal data class VesselEditorRoute(val vesselId: String? = null)

/** The four areas of 03 §2.2, in navigation order. */
internal enum class Tab(val root: Any, @StringRes val label: Int, @DrawableRes val icon: Int) {
    Start(StartRoute, R.string.tab_start, UmamiIcons.Start),
    Journal(JournalRoute, R.string.tab_journal, UmamiIcons.Journal),
    Collection(CollectionRoute, R.string.tab_collection, UmamiIcons.Collection),
    Stats(StatsRoute, R.string.tab_stats, UmamiIcons.Statistics),
}
