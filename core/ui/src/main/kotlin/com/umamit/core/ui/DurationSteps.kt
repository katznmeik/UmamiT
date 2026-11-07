package com.umamit.core.ui

/**
 * The rules of „Dauer wählen" ([DurationPicker]; SCR-07, SCR-31): the picker moves hours, minutes or seconds;
 * every result is a duration a step may have (PRF-04): 5 s – 24 h, in 5 s below 10 min and in whole minutes
 * from 10 min on. A move that would leave the bounds is not offered.
 */
object DurationSteps {
    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 86_400

    private const val LONG_FROM_SECONDS = 600
    private const val SHORT_UNIT = 5
    private const val LONG_UNIT = 60
    private const val SECONDS_PER_HOUR = 3_600

    /** The picker's three fields. */
    enum class Field(internal val seconds: Int) {
        HOURS(SECONDS_PER_HOUR),
        MINUTES(LONG_UNIT),
        SECONDS(SHORT_UNIT),
    }

    /**
     * [seconds] with [field] one up or down, rounded to the PRF-04 increment;
     * null when that move is not offered (outside 5 s – 24 h, or seconds from
     * 10 min on).
     */
    fun step(seconds: Int, field: Field, up: Boolean): Int? {
        val secondsMovable = if (up) seconds < LONG_FROM_SECONDS else seconds <= LONG_FROM_SECONDS
        if (field == Field.SECONDS && !secondsMovable) return null
        val raw = seconds + if (up) field.seconds else -field.seconds
        return if (raw in MIN_SECONDS..MAX_SECONDS) rounded(raw).takeIf { it != seconds } else null
    }

    /** Hours, minutes and seconds of [seconds]: „1 Std. 2 Min. 5 Sek.". */
    fun parts(seconds: Int): Triple<Int, Int, Int> =
        Triple(seconds / SECONDS_PER_HOUR, seconds % SECONDS_PER_HOUR / LONG_UNIT, seconds % LONG_UNIT)

    /**
     * The duration stepper's − / + (SCR-12, as the Schritte-Sheet's): the next multiple of 5 s below 10 min and of
     * 1 min from 10 min on, up or down, within 5 s – 24 h.
     */
    fun stepped(seconds: Int, up: Boolean): Int {
        val next = if (up) {
            val unit = if (seconds < LONG_FROM_SECONDS) SHORT_UNIT else LONG_UNIT
            Math.floorDiv(seconds, unit) * unit + unit
        } else {
            val unit = if (seconds > LONG_FROM_SECONDS) LONG_UNIT else SHORT_UNIT
            Math.floorDiv(seconds - 1, unit) * unit
        }
        return next.coerceIn(MIN_SECONDS, MAX_SECONDS)
    }

    /** [seconds] within 5 s – 24 h, rounded down to the PRF-04 increment. */
    fun rounded(seconds: Int): Int {
        val bounded = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        val unit = if (bounded < LONG_FROM_SECONDS) SHORT_UNIT else LONG_UNIT
        return (bounded / unit * unit).coerceIn(MIN_SECONDS, MAX_SECONDS)
    }
}
