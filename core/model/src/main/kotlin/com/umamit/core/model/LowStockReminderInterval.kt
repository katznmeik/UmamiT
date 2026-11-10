package com.umamit.core.model

/**
 * SET-28 *Vorrats-Erinnerung* (INV-11; a switch in v0.3.0, D-098; this choice from v0.5.0, D-108 point 7):
 * every [days] days from the last reminder posted, or [OFF]. Stored by name; an unknown name reads as [DAILY].
 */
@Suppress("MagicNumber") // the days are the choices themselves (D-108 point 7)
enum class LowStockReminderInterval(val days: Int) {
    DAILY(1),
    EVERY_2_DAYS(2),
    EVERY_3_DAYS(3),
    WEEKLY(7),
    OFF(0),
    ;

    val isOn: Boolean get() = this != OFF
}
