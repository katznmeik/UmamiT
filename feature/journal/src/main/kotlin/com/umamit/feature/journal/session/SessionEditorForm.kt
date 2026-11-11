package com.umamit.feature.journal.session

import com.umamit.core.data.SessionEdit
import com.umamit.core.data.SessionEditLimits
import com.umamit.core.data.StepEdit
import com.umamit.core.data.VesselChange
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.domain.brew.BrewScaling
import com.umamit.core.model.BrewSession
import com.umamit.core.model.SessionStepRow
import com.umamit.core.ui.GermanFormat
import java.io.Serializable
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A brewed step's fields in the session editor (SCR-17): the recorded time as typed („1:35") and the °C. */
data class SessionEditorStepForm(val position: Int, val time: String, val tempC: Int) : Serializable {
    /** The step with the temperature one 5 °C step up or down, within the Schritte-Sheet's range (GEN-12). */
    fun withTempStep(up: Boolean): SessionEditorStepForm {
        val next = if (up) {
            Math.floorDiv(tempC, TEMP_UNIT) * TEMP_UNIT + TEMP_UNIT
        } else {
            Math.floorDiv(tempC - 1, TEMP_UNIT) * TEMP_UNIT
        }
        return copy(tempC = next.coerceIn(SessionEditLimits.TEMP_C.first, SessionEditLimits.TEMP_C.last))
    }

    private companion object {
        private const val serialVersionUID = 1L
        const val TEMP_UNIT = 5
    }
}

/**
 * The session editor's fields as typed (SCR-17, JRN-11): the start, the vessel, leaf and water as text (so a
 * half-typed number stays as it is) and the brewed steps. It lives in the `SavedStateHandle`, so the unsaved values
 * survive an alert, a configuration change and a process death (rule 8).
 */
data class SessionEditorForm(
    val startedAtMs: Long,
    val vesselId: String?,
    val leaf: String,
    val water: String,
    val steps: List<SessionEditorStepForm>,
) : Serializable {
    /** The form with the start on [date], at the same time of day in [zone]. */
    fun withDate(date: LocalDate, zone: ZoneId): SessionEditorForm {
        val start = startIn(zone)
        return copy(startedAtMs = ZonedDateTime.of(date, start.toLocalTime(), zone).toInstant().toEpochMilli())
    }

    /** The form with the start at [hour]:[minute] on the same day in [zone]; the seconds stay. */
    fun withTime(hour: Int, minute: Int, zone: ZoneId): SessionEditorForm {
        val start = startIn(zone)
        val time = LocalTime.of(hour, minute, start.second, start.nano)
        return copy(startedAtMs = ZonedDateTime.of(start.toLocalDate(), time, zone).toInstant().toEpochMilli())
    }

    fun withStep(position: Int, transform: (SessionEditorStepForm) -> SessionEditorStepForm): SessionEditorForm =
        copy(steps = steps.map { if (it.position == position) transform(it) else it })

    fun startIn(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(startedAtMs).atZone(zone)

    companion object {
        private const val serialVersionUID = 1L

        /** The form of [session] as recorded. */
        fun of(session: BrewSession): SessionEditorForm = SessionEditorForm(
            startedAtMs = session.startedAtMs,
            vesselId = session.snapshot.vesselId,
            leaf = session.snapshot.leafGrams?.let(GermanFormat::gramsOneDecimal).orEmpty(),
            water = session.snapshot.waterMl?.toString().orEmpty(),
            steps = session.steps.filter { it.isEditable() }.map { row ->
                SessionEditorStepForm(
                    position = row.position,
                    time = clockText(checkNotNull(row.actualSeconds)),
                    tempC = row.tempC ?: session.snapshot.baseTempC ?: DEFAULT_TEMP_C,
                )
            },
        )

        /** The temperature shown for a step without one and a session without a base (the recipe editor's default). */
        private const val DEFAULT_TEMP_C = 70
    }
}

/** A brewed step can be edited; never started and skipped ones show greyed (SCR-17). */
internal fun SessionStepRow.isEditable(): Boolean = actualSeconds != null && !skipped

/** A field the editor refuses, shown at the field; *Speichern* stays greyed out while any is shown. */
enum class SessionEditorError { Leaf, Water, InFuture }

/** What the form means against the [session] as recorded. */
internal class SessionEditorCheck(private val session: BrewSession, private val form: SessionEditorForm, nowMs: Long) {
    private val stored = session.snapshot

    /** The parsed leaf; null when the field is not valid. An empty field is valid only for a session without leaf. */
    val leaf: Double? = parseLeaf(form.leaf)
    private val leafValid = leaf != null || (form.leaf.isBlank() && stored.leafGrams == null)

    private val water: Int? = parseWater(form.water, stored.waterMl)
    private val waterValid = water != null || (form.water.isBlank() && stored.waterMl == null)

    private val shift = form.startedAtMs - session.startedAtMs
    private val inFuture = (session.endedAtMs ?: session.startedAtMs) + shift > nowMs

    /** The positions of the steps whose time is not a valid entry. */
    val invalidSteps: Set<Int> = form.steps.filter { parseClock(it.time) == null }.map { it.position }.toSet()

    val errors: Set<SessionEditorError> = buildSet {
        if (!leafValid) add(SessionEditorError.Leaf)
        if (!waterValid) add(SessionEditorError.Water)
        if (inFuture) add(SessionEditorError.InFuture)
    }

    val valid: Boolean get() = errors.isEmpty() && invalidSteps.isEmpty()

    /** The leaf differs from the recorded one (and is valid): the stock line's change in g. */
    val leafChange: Double? = leaf?.takeIf { it != stored.leafGrams }

    /** The edit for `EditSession`: only what differs from the recorded values; null while a field is invalid. */
    fun edit(): SessionEdit? {
        if (!valid) return null
        val rows = session.steps.associateBy { it.position }
        val initial = SessionEditorForm.of(session).steps.associateBy { it.position }
        val steps = form.steps.mapNotNull { step ->
            val row = rows[step.position] ?: return@mapNotNull null
            val seconds = checkNotNull(parseClock(step.time))
            // Against the form's initial value, not the stored one: a step shown with a fallback °C (no recorded
            // temperature) writes a temperature only when the user changed it.
            val tempC = step.tempC.takeIf { it != initial[step.position]?.tempC }
            StepEdit(step.position, seconds, tempC).takeIf { seconds != row.actualSeconds || tempC != null }
        }
        return SessionEdit(
            startedAtMs = form.startedAtMs.takeIf { shift != 0L },
            vessel = VesselChange(form.vesselId).takeIf { form.vesselId != stored.vesselId },
            leafGrams = leafChange,
            waterMl = water?.takeIf { it != stored.waterMl },
            steps = steps,
        )
    }

    /** Something differs from the recorded values (valid or not). */
    val hasChanges: Boolean = form != SessionEditorForm.of(session) && (edit()?.isEmpty() != true)

    private fun SessionEdit.isEmpty(): Boolean =
        startedAtMs == null && vessel == null && leafGrams == null && waterMl == null && steps.isEmpty()

    companion object {
        private val LEAF = Regex("""\d{1,3}(?:[.,]\d)?""")
        private const val WATER_UNIT = 5
        private val CLOCK = Regex("""(?:(\d{1,2}):)?(\d{1,4}):(\d{2})""")
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 3_600

        /** „4,5" → 4.5 g within the setup's 0,1–100 g (BrewScaling); null otherwise. */
        fun parseLeaf(text: String): Double? = text.trim().takeIf(LEAF::matches)
            ?.replace(',', '.')?.toDouble()
            ?.takeIf { it >= BrewScaling.MIN_LEAF_GRAMS && it <= BrewScaling.MAX_LEAF_GRAMS }

        /** „250" → 250 ml within 10–3000 ml in 5-ml steps (the setup's); the recorded [stored] value stays valid. */
        fun parseWater(text: String, stored: Int?): Int? = text.trim()
            .takeIf { it.isNotEmpty() && it.length <= WATER_DIGITS && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf {
                it == stored || (it in BrewScaling.MIN_WATER_ML..BrewScaling.MAX_WATER_ML && it % WATER_UNIT == 0)
            }

        /** „1:35", „0:07", „75:00" or „1:15:00" → seconds within 1 s – 24 h; null otherwise. */
        fun parseClock(text: String): Int? = CLOCK.matchEntire(text.trim())?.destructured?.let { (h, m, sec) ->
            val minutes = m.toInt()
            val seconds = sec.toInt()
            val valid = seconds < SECONDS_PER_MINUTE && (h.isEmpty() || minutes < SECONDS_PER_MINUTE)
            val total = (h.toIntOrNull() ?: 0) * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
            total.takeIf { valid && it in SessionEditLimits.STEP_SECONDS }
        }

        private const val WATER_DIGITS = 4
    }
}

/** |[a] − [b]| to 0,1 g, exact („2", „0,5"). */
internal fun gramsDifference(a: Double, b: Double): Double =
    (a.toBigDecimal() - b.toBigDecimal()).abs().setScale(1, RoundingMode.HALF_UP).toDouble()

/** The session's zone; one this device doesn't know counts as the device's (as on the page, D-054). */
internal fun sessionZone(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
