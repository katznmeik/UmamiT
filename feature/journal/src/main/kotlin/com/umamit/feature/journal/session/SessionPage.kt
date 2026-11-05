package com.umamit.feature.journal.session

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.StepKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What became of a session's tea (BRW-06, D-100). */
enum class SessionTea {
    /** In the collection: the name links to its detail, and *So nochmal brühen* is offered. */
    AVAILABLE,

    /** Archived: *So nochmal brühen* is greyed out with „Tee ist archiviert" and *Wiederherstellen*. */
    ARCHIVED,

    /** Deleted: the name without a link, and no *So nochmal brühen*. */
    DELETED,
}

/**
 * One row of the step table (SCR-16): the step's kind, its set time (*geplant*, including Schritte-Sheet changes) and
 * its measured time (*tatsächlich*, until *Abgegossen*, overtime included; 11 „Set time / measured time").
 *
 * @property infusionNumber the step's number among the session's infusions („Aufguss 2"); null for a rinse or a whisk.
 * @property actualSeconds null for a step never started, kept from v0.4.0 (Q-19): it reads „nicht gebrüht".
 * @property tempC the step's temperature, the session's base where the step had none.
 */
data class SessionPageStep(
    val kind: StepKind,
    val infusionNumber: Int?,
    val plannedSeconds: Int,
    val actualSeconds: Int?,
    val tempC: Int?,
    val skipped: Boolean,
) {
    val brewed: Boolean get() = actualSeconds != null
}

/**
 * The session page (SCR-16, read-only in v0.4.0, D-100): the tea, the start in the session's own time zone, method,
 * vessel, leaf / water / ratio and the step table, all from the session's snapshot (05 §1.5), so a renamed or deleted
 * tea never changes it.
 *
 * @property teaId the tea to link to, null when it is deleted.
 */
data class SessionPage(
    val sessionId: String,
    val teaId: String?,
    val teaName: String,
    val teaSecondaryName: String?,
    val styleKey: String?,
    val tea: SessionTea,
    val startedAt: LocalDateTime,
    val method: BrewMethod,
    val vesselName: String?,
    val leafGrams: Double?,
    val waterMl: Int?,
    val steps: List<SessionPageStep>,
) {
    /** Leaf per 100 ml, to 0,1 g („2,5 g / 100 ml", as in the setup); null without leaf or water. */
    val ratioPer100Ml: Double?
        get() {
            val leaf = leafGrams ?: return null
            val water = waterMl?.takeIf { it > 0 } ?: return null
            return (leaf.toBigDecimal() * HUNDRED).divide(water.toBigDecimal(), 1, RoundingMode.HALF_UP).toDouble()
        }

    /** *So nochmal brühen* is enabled: the tea is in the collection (BRW-06). */
    val canBrewAgain: Boolean get() = tea == SessionTea.AVAILABLE

    private companion object {
        val HUNDRED = BigDecimal(100)
    }
}

/** The page of [session], whose tea is now [tea]. Every stored step is listed, never-started ones included (Q-19). */
fun sessionPage(session: BrewSession, tea: SessionTea): SessionPage {
    val snapshot = session.snapshot
    var infusions = 0
    val steps = session.steps.sortedBy { it.position }.map { row ->
        SessionPageStep(
            kind = row.kind,
            infusionNumber = if (row.kind == StepKind.INFUSION) ++infusions else null,
            plannedSeconds = row.plannedSeconds,
            actualSeconds = row.actualSeconds,
            tempC = row.tempC ?: snapshot.baseTempC,
            skipped = row.skipped,
        )
    }
    return SessionPage(
        sessionId = session.id,
        teaId = snapshot.teaId.takeIf { tea != SessionTea.DELETED },
        teaName = snapshot.teaName,
        teaSecondaryName = snapshot.teaSecondaryName,
        styleKey = snapshot.styleKey,
        tea = tea,
        startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAtMs), zoneOf(session.zoneId)),
        method = snapshot.method,
        vesselName = snapshot.vesselName,
        leafGrams = snapshot.leafGrams,
        waterMl = snapshot.waterMl,
        steps = steps,
    )
}

/** The session's zone; one this device doesn't know counts as the device's (as in the journal, D-054). */
private fun zoneOf(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
