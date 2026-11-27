package com.umamit.feature.onboarding.mockup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.umamit.feature.onboarding.R

/**
 * The named mock-ups (SCR-36 table): each with its TalkBack [description] and its [content]. The six of the tour
 * (SCR-33) come first; #367 adds the Anleitungen' ones here (M-SORTE … M-BANNER).
 */
enum class HelpMockupId(@StringRes val description: Int, val content: @Composable () -> Unit) {
    /** Start, *Aufguss starten* marked. */
    Start(R.string.mockup_description_start, { StartMockup() }),

    /** The timer, the other timer's row under the band marked. */
    Two(R.string.mockup_description_two, { TwoTimersMockup() }),

    /** *Meine Tees* with ⋮ open, *Tee ohne Katalog hinzufügen* marked. */
    MyTeasMenu(R.string.mockup_description_my_teas_menu, { MyTeasMenuMockup() }),

    /** A session page, the stars and *Geschmack hinzufügen* marked. */
    Session(R.string.mockup_description_session, { SessionMockup() }),

    /** Statistik › *Verbrauch*, the cost per litre marked. */
    Stats(R.string.mockup_description_stats, { StatsMockup() }),

    /** *Daten & Sicherung*, *Sicherung erstellen* marked. */
    Data(R.string.mockup_description_data, { DataMockup() }),

    /** *Sorte wählen*, the row Sencha marked. */
    Sorte(R.string.mockup_description_sorte, { SorteMockup() }),

    /** *Neuer Tee*, the *Kauf* section marked. */
    TeaNew(R.string.mockup_description_tea_new, { TeaNewMockup() }),

    /** *Katalog* with ⋮ open, *Link einfügen* marked. */
    CatalogMenu(R.string.mockup_description_catalog_menu, { CatalogMenuMockup() }),

    /** A catalog tea, *In Sammlung aufnehmen* marked. */
    CatalogTea(R.string.mockup_description_catalog_tea, { CatalogTeaMockup() }),

    /** The sheet *In Sammlung aufnehmen*, *Aufnehmen* marked. */
    AddSheet(R.string.mockup_description_add_sheet, { AddSheetMockup() }),

    /** A tea's detail, *Vorrat* with the cost lines marked. */
    Stock(R.string.mockup_description_stock, { StockMockup() }),

    /** The Vorrat history, a *Kauf* row marked. */
    History(R.string.mockup_description_history, { HistoryMockup() }),

    /** A tea's detail, *Rezepte* with *Rezept hinzufügen* marked. */
    Recipes(R.string.mockup_description_recipes, { RecipesMockup() }),

    /** *Rezept bearbeiten*, a step's kind menu marked. */
    Steps(R.string.mockup_description_steps, { StepsMockup() }),

    /** *Rezept übernehmen*, *Für welchen Tee?* marked. */
    Receive(R.string.mockup_description_receive, { ReceiveMockup() }),

    /** *Neue Session*, *Gefäß* marked. */
    Setup(R.string.mockup_description_setup, { SetupMockup() }),

    /** The timer with ⋮ open, *Schritte bearbeiten* marked. */
    TimerMenu(R.string.mockup_description_timer_menu, { TimerMenuMockup() }),

    /** *Gespeichert im Journal*, the stars marked. */
    Saved(R.string.mockup_description_saved, { SavedMockup() }),

    /** *Einstellungen*, *Daten & Sicherung* marked. */
    SettingsData(R.string.mockup_description_settings_data, { SettingsDataMockup() }),

    /** *Einstellungen*, the card *Timer-Zuverlässigkeit* with *Beheben* marked. */
    Reliability(R.string.mockup_description_reliability, { ReliabilityMockup() }),

    /** Start with the banner *Der Timer ist eventuell unzuverlässig* marked. */
    Banner(R.string.mockup_description_banner, { BannerMockup() }),
}
