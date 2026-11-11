package com.umamit.feature.journal.session

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SessionEdit
import com.umamit.core.data.StepEdit
import com.umamit.core.data.VesselChange
import com.umamit.feature.journal.JournalSessions
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** The editor's form rules (SCR-17, JRN-11): the entries it reads and the edit it hands to `EditSession`. */
class SessionEditorFormTest {
    private val session = JournalSessions.session(
        "past",
        LocalDateTime.of(2026, 10, 22, 9, 12, 30),
        steps = listOf(
            JournalSessions.step(0, 60, 62, tempC = 60),
            JournalSessions.step(1, 20, 0, skipped = true),
            JournalSessions.step(2, 30, null),
        ),
    ).session
    private val now = JournalSessions.ms(LocalDateTime.of(2026, 10, 23, 8, 0))

    private fun check(form: SessionEditorForm) = SessionEditorCheck(session, form, now)

    @Test
    fun `the form of a session lists only its brewed steps, with the recorded time`() {
        val form = SessionEditorForm.of(session)

        assertThat(form.leaf).isEqualTo("4,0")
        assertThat(form.water).isEqualTo("250")
        assertThat(form.steps).containsExactly(SessionEditorStepForm(0, "1:02", 60))
        assertThat(check(form).hasChanges).isFalse()
        assertThat(check(form).edit()).isEqualTo(SessionEdit())
    }

    @Test
    fun `times are read as m_ss or h_mm_ss within 1 s and 24 h`() {
        assertThat(SessionEditorCheck.parseClock("1:35")).isEqualTo(95)
        assertThat(SessionEditorCheck.parseClock("0:01")).isEqualTo(1)
        assertThat(SessionEditorCheck.parseClock("75:00")).isEqualTo(4_500)
        assertThat(SessionEditorCheck.parseClock("1:15:00")).isEqualTo(4_500)
        assertThat(SessionEditorCheck.parseClock("24:00:00")).isEqualTo(86_400)
        listOf("0:00", "1:60", "1:5", "abc", "", "1:60:00", "24:00:01").forEach {
            assertThat(SessionEditorCheck.parseClock(it)).isNull()
        }
    }

    @Test
    fun `only what differs goes into the edit`() {
        val zone = JournalSessions.BERLIN
        val form = SessionEditorForm.of(session)
            .withDate(LocalDate.of(2026, 10, 21), zone)
            .copy(vesselId = "v1", leaf = "4,5", water = "250")
            .withStep(0) { it.copy(time = "1:10") }

        val edit = checkNotNull(check(form).edit())

        assertThat(edit.startedAtMs).isEqualTo(JournalSessions.ms(LocalDateTime.of(2026, 10, 21, 9, 12, 30)))
        assertThat(edit.vessel).isEqualTo(VesselChange("v1"))
        assertThat(edit.leafGrams).isEqualTo(4.5)
        assertThat(edit.waterMl).isNull()
        assertThat(edit.steps).containsExactly(StepEdit(0, 70))
    }

    @Test
    fun `a step without a recorded temperature gets one only when the user changes it`() {
        val bare = JournalSessions.session(
            "bare",
            LocalDateTime.of(2026, 10, 22, 9, 12, 30),
            baseTempC = null,
            steps = listOf(JournalSessions.step(0, 60, 62)),
        ).session
        val form = SessionEditorForm.of(bare)
        val check = { f: SessionEditorForm -> checkNotNull(SessionEditorCheck(bare, f, now).edit()) }

        assertThat(check(form.copy(water = "300")).steps).isEmpty()
        assertThat(check(form.withStep(0) { it.copy(time = "1:10") }).steps).containsExactly(StepEdit(0, 70))
        assertThat(check(form.withStep(0) { it.withTempStep(up = true) }).steps)
            .containsExactly(StepEdit(0, 62, 75))
    }

    @Test
    fun `a new time keeps the seconds of the start, and the same time is no change`() {
        val zone = JournalSessions.BERLIN
        val form = SessionEditorForm.of(session)

        assertThat(check(form.withTime(9, 12, zone)).hasChanges).isFalse()
        assertThat(form.withTime(10, 0, zone).startIn(zone).toLocalTime().toString()).isEqualTo("10:00:30")
    }

    @Test
    fun `the start is refused when the end would be after now`() {
        val form = SessionEditorForm.of(session).withDate(LocalDate.of(2026, 10, 23), JournalSessions.BERLIN)

        assertThat(check(form).errors).containsExactly(SessionEditorError.InFuture)
        assertThat(check(form).edit()).isNull()
    }

    @Test
    fun `the recorded water stays valid, a new one needs 5-ml steps`() {
        assertThat(SessionEditorCheck.parseWater("133", stored = 133)).isEqualTo(133)
        assertThat(SessionEditorCheck.parseWater("134", stored = 133)).isNull()
        assertThat(SessionEditorCheck.parseWater("135", stored = 133)).isEqualTo(135)
        assertThat(SessionEditorCheck.parseWater("5", stored = 133)).isNull()
    }

    @Test
    fun `temperatures step by 5 within 5 to 100`() {
        val step = SessionEditorStepForm(0, "1:00", 62)
        assertThat(step.withTempStep(up = true).tempC).isEqualTo(65)
        assertThat(step.withTempStep(up = false).tempC).isEqualTo(60)
        assertThat(SessionEditorStepForm(0, "1:00", 100).withTempStep(up = true).tempC).isEqualTo(100)
        assertThat(SessionEditorStepForm(0, "1:00", 5).withTempStep(up = false).tempC).isEqualTo(5)
    }
}
