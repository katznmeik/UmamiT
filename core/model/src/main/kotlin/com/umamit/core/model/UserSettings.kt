package com.umamit.core.model

/** The settings of 05 §6 the app has so far (M1 subset, M2 catalog), with the spec defaults. */
data class UserSettings(
    val schemaVersion: Int = 1,
    val onboardingCompleted: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val alertSound: String = DEFAULT_ALERT_SOUND,
    /** SET-26 *Vorwarnton*, the key of the pre-alert's sound; an unknown key plays the default (05 §6). */
    val preAlertSound: String = DEFAULT_PRE_ALERT_SOUND,
    /** 0 = off, otherwise 5–30 in steps of 5 (SET-10). */
    val preAlertSeconds: Int = 10,
    val vibration: Boolean = true,
    val alertLength: AlertLength = AlertLength.NORMAL,
    val deductStock: Boolean = true,
    val lowStockGrams: Double = 10.0,
    val keepScreenOn: Boolean = true,
    val dimWhileSteeping: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
    /** SET-22 *Katalog: Fotos laden*: the catalog refresh downloads the teas' photos (CAT-08). */
    val catalogDownloadPhotos: Boolean = true,
) {
    companion object {
        const val DEFAULT_ALERT_SOUND = "klangschale"

        /** SET-26 *Vorwarnton*: the Zimbel until the PO picks the default with the first v0.2.1 rc (D-093). */
        const val DEFAULT_PRE_ALERT_SOUND = "zimbel"
    }
}
