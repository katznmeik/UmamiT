package com.umamit.feature.brew.quick

/**
 * The durations of the quick timer (SCR-07, TMR-22): the chips, and the
 * duration picker's rules. The picker moves hours, minutes or seconds; every
 * result is a duration a step may have (PRF-04): 5 s – 24 h, in 5 s below
 * 10 min and in whole minutes from 10 min on. A move that would leave the
 * bounds is not offered.
 */
object QuickDuration {
    /** 0:30, 1:00, 1:30, 2:00, 3:00, 5:00, 8:00. */
    val CHIPS: List<Int> = listOf(30, 60, 90, 120, 180, 300, 480)

    /** What SCR-07 opens with: 1:00. */
    const val DEFAULT_SECONDS = 60

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

    private fun rounded(seconds: Int): Int {
        val unit = if (seconds < LONG_FROM_SECONDS) SHORT_UNIT else LONG_UNIT
        return (seconds / unit * unit).coerceIn(MIN_SECONDS, MAX_SECONDS)
    }
}
