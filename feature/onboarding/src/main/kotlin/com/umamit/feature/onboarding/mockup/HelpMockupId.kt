package com.umamit.feature.onboarding.mockup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.umamit.feature.onboarding.R

/**
 * The named mock-ups (SCR-36 table): each with its TalkBack [description] and its [content]. The six of the tour
 * (SCR-33) come first; #367 adds the Anleitungen' ones here.
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
}
