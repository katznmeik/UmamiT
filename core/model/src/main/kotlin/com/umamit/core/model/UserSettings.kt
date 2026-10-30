package com.umamit.core.model

/** The settings of 05 §6 the app has so far (M1 subset, M2 catalog), with the spec defaults. */
data class UserSettings(
    /** The settings' format version (05 §6): 2 since v0.2.2, whose migration moved the old 10 g default (D-097). */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val onboardingCompleted: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val alertSound: String = DEFAULT_ALERT_SOUND,
    /** SET-26 *Vorwarnton*, the key of the pre-alert's sound; an unknown key plays the default (05 §6). */
    val preAlertSound: String = DEFAULT_PRE_ALERT_SOUND,
    /**
     * SET-27 *Lautstärke* (v0.3.0, D-098): the gain of Vorwarnung and alarm on top of the phone's alarm
     * volume, 10–100 % in steps of 10; never below 10, so the app itself can never silence a cue.
     */
    val alertVolumePercent: Int = MAX_ALERT_VOLUME_PERCENT,
    /** 0 = off, otherwise 5–30 in steps of 5 (SET-10). */
    val preAlertSeconds: Int = 10,
    val vibration: Boolean = true,
    val alertLength: AlertLength = AlertLength.NORMAL,
    val deductStock: Boolean = true,
    /** SET-16 „Knapp ab" (INV-05): 0–500 g in steps of 5; a tea's own `low_stock_grams` wins over it. */
    val lowStockGrams: Double = DEFAULT_LOW_STOCK_GRAMS,
    /**
     * INV-10 *Ausblenden*: `<tea id>/<id of its latest INITIAL or PURCHASE event>` per hidden low-stock card;
     * a newer purchase of the tea has another id, so its card shows again (D-097).
     */
    val lowStockHidden: Set<String> = emptySet(),
    val keepScreenOn: Boolean = true,
    val dimWhileSteeping: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
    /** SET-22 *Katalog: Fotos laden*: the catalog refresh downloads the teas' photos (CAT-08). */
    val catalogDownloadPhotos: Boolean = true,
    /**
     * TMR-22 (v0.2.2, D-097): the last *Vorwarnung* chosen on SCR-07, which the next quick timer opens with:
     * null *Automatisch*, 0 *Aus*, otherwise 5–30 in steps of 5.
     */
    val quickTimerPreAlert: Int? = null,
) {
    companion object {
        const val DEFAULT_ALERT_SOUND = "klangschale"

        /** The settings' format version of this app (05 §6). */
        const val CURRENT_SCHEMA_VERSION = 2

        /** SET-16's default from v0.2.2 (D-097; was 10 g). */
        const val DEFAULT_LOW_STOCK_GRAMS = 15.0

        /** SET-26 *Vorwarnton*: the Zimbel until the PO picks the default with the first v0.2.1 rc (D-093). */
        const val DEFAULT_PRE_ALERT_SOUND = "zimbel"

        /** SET-27 *Lautstärke*: 10–100 % in steps of 10, default 100 % (D-098). */
        const val MIN_ALERT_VOLUME_PERCENT = 10
        const val MAX_ALERT_VOLUME_PERCENT = 100
        const val ALERT_VOLUME_STEP_PERCENT = 10
    }
}
