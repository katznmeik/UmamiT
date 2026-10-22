package com.umamit.core.model

/** The M1 subset of the settings of 05 §6, with the spec defaults. */
data class UserSettings(
    val schemaVersion: Int = 1,
    val onboardingCompleted: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val alertSound: String = DEFAULT_ALERT_SOUND,
    /** 0 = off, otherwise 5–30 in steps of 5 (SET-10). */
    val preAlertSeconds: Int = 10,
    val vibration: Boolean = true,
    val alertLength: AlertLength = AlertLength.NORMAL,
    val deductStock: Boolean = true,
    val lowStockGrams: Double = 10.0,
    val keepScreenOn: Boolean = true,
    val dimWhileSteeping: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_ALERT_SOUND = "klangschale"
    }
}
