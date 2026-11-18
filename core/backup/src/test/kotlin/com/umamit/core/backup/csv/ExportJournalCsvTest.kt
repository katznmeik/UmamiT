package com.umamit.core.backup.csv

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteSet
import com.umamit.core.model.TeaCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.time.measureTime

/** *Journal als CSV* (BAK-04, D-130 Q9, Q10, #337): the bytes against a golden, and 1 000 sessions in time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportJournalCsvTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, UmamiDatabase::class.java).build()
    private val labels = ResourceJournalCsvLabels(context.resources, DESCRIPTOR_LABELS::get)
    private val export = ExportJournalCsv(db)

    @After
    fun tearDown() = db.close()

    private fun exportBytes(): Pair<Int, ByteArray> = runBlocking {
        val out = ByteArrayOutputStream()
        val count = export(out, labels)
        count to out.toByteArray()
    }

    @Test
    fun `a synthetic journal exports as the golden, byte for byte`() = runBlocking {
        insertJournal()

        val (count, bytes) = exportBytes()

        assertThat(count).isEqualTo(5)
        assertThat(bytes).isEqualTo(GOLDEN.toByteArray(Charsets.UTF_8))
        // UTF-8's byte order mark first, so German Excel reads the umlauts (D-130 Q9).
        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()).inOrder()
    }

    @Test
    fun `an empty journal exports the header row alone`() {
        val (count, bytes) = exportBytes()

        assertThat(count).isEqualTo(0)
        assertThat(bytes.toString(Charsets.UTF_8)).isEqualTo(BOM + HEADER + CRLF)
    }

    @Test
    fun `1 000 sessions export in under 2 s`() = runBlocking {
        db.withTransaction {
            db.teaDao().upsert(csvTea("t", "Testtee", TeaCategory.GREEN))
            repeat(SESSIONS) { index ->
                val id = "s$index"
                val start = at("2026-01-01T08:00:00") + index * 3_600_000L
                db.sessionDao().insert(
                    csvSession(
                        id = id,
                        teaId = "t",
                        teaName = "Testtee",
                        category = TeaCategory.GREEN,
                        startedAt = start,
                        plannedStepCount = 4,
                        baseTempC = 60,
                        profileName = "Kyusu",
                        leafGrams = 4.0,
                        waterMl = 120,
                        costCents = 36,
                        rating = 4.5,
                        notes = "Notiz $index; mit \"Anführung\"\nund Zeile",
                    ),
                )
                db.sessionDao().insertSteps(
                    listOf(
                        csvStep(id, 0, StepKind.RINSE, actualSeconds = 10, startedAt = start),
                        csvStep(id, 1, actualSeconds = 60, umami = 4, sweetness = 3),
                        csvStep(id, 2, actualSeconds = 20, tempC = 65, umami = 5),
                        csvStep(id, 3, actualSeconds = 45, tempC = 70, sweetness = 2),
                    ),
                )
                db.tastingDao().insertDescriptor(csvDescriptor(id, "marine.nori", 1))
                db.tastingDao().insertDescriptor(csvDescriptor(id, "vegetal.spinat", 2))
            }
        }
        exportBytes() // warm-up: Room's statements, the formatters, the resources

        var count = 0
        val took = measureTime { count = exportBytes().first }

        assertThat(count).isEqualTo(SESSIONS)
        assertThat(took.inWholeMilliseconds).isLessThan(2_000)
    }

    /**
     * Six sessions, one still in progress: a green one with a rinse, a never-started step, a hidden Röstung value,
     * three descriptors (one unknown) and a note with `;`, quotes and a line break; a herbal one with a hidden Umami
     * value, a skipped step and no temperature; a green tea whose *Geschmacksprofil* is the oxidised set; a `MANUAL`
     * session in another time zone whose oolong tea is gone (its snapshot Kategorie decides the set); a session
     * without tea or Kategorie. A brewed step's actual time differs from its planned one, so the golden pins it.
     */
    private suspend fun insertJournal() = db.withTransaction {
        db.teaDao().upsert(csvTea("t-green", "Nebelhang Sencha", TeaCategory.GREEN))
        db.teaDao().upsert(csvTea("t-herbal", "Wiesen \"Kräuter\"; Mischung", TeaCategory.HERBAL))
        db.teaDao().upsert(csvTea("t-override", "Rauchgrün", TeaCategory.GREEN, TasteSet.OXIDISED))
        insertGreen()
        insertHerbal()
        insertOverrideGoneAndRunning()
        insertUncategorised()
    }

    private suspend fun insertGreen() {
        val greenStart = at("2026-10-21T07:45:10")
        val firstStep = at("2026-10-21T07:45:30")
        db.sessionDao().insert(
            csvSession(
                id = "s-green",
                teaId = "t-green",
                teaName = "Nebelhang Sencha",
                category = TeaCategory.GREEN,
                startedAt = greenStart,
                endedAt = firstStep + 750_000,
                plannedStepCount = 5,
                baseTempC = 60,
                profileName = "Kyusu",
                vesselName = "Shiboridashi",
                leafGrams = 4.0,
                waterMl = 120,
                costCents = 36,
                rating = 4.5,
                brewingRating = 4.0,
                notes = "Süß; \"weich\"\nzweite Zeile",
            ),
        )
        db.sessionDao().insertSteps(
            listOf(
                csvStep("s-green", 0, StepKind.RINSE, actualSeconds = 10, tempC = 60, startedAt = firstStep),
                csvStep("s-green", 1, actualSeconds = 93, plannedSeconds = 90, umami = 4, sweetness = 3, roast = 5),
                csvStep("s-green", 2, actualSeconds = 20, tempC = 65, umami = 5),
                csvStep("s-green", 3, actualSeconds = 45, tempC = 70),
                csvStep("s-green", 4, actualSeconds = null, tempC = 75),
            ),
        )
        db.tastingDao().insertDescriptor(csvDescriptor("s-green", "vegetal.spinat", 2))
        db.tastingDao().insertDescriptor(csvDescriptor("s-green", "marine.nori", 1))
        db.tastingDao().insertDescriptor(csvDescriptor("s-green", "unknown.key", 3))
    }

    private suspend fun insertHerbal() {
        val herbalStart = at("2026-10-22T19:05:00")
        db.sessionDao().insert(
            csvSession(
                id = "s-herbal",
                teaId = "t-herbal",
                teaName = "Wiesen \"Kräuter\"; Mischung",
                category = TeaCategory.HERBAL,
                startedAt = herbalStart,
                endedAt = herbalStart + 330_000,
                plannedStepCount = 2,
            ),
        )
        db.sessionDao().insertSteps(
            listOf(
                csvStep("s-herbal", 0, actualSeconds = 300, umami = 5, sweetness = 2, acidity = 3, freshness = 4),
                csvStep("s-herbal", 1, actualSeconds = 0, skipped = true),
            ),
        )
    }

    private suspend fun insertOverrideGoneAndRunning() {
        db.sessionDao().insert(
            csvSession(
                id = "s-override",
                teaId = "t-override",
                teaName = "Rauchgrün",
                category = TeaCategory.GREEN,
                startedAt = at("2026-10-23T06:00:00"),
                vesselName = "+Glas; klein",
                notes = "- etwas bitter",
            ),
        )
        db.sessionDao().insertSteps(listOf(csvStep("s-override", 0, tempC = 80, umami = 5, roast = 2)))

        db.sessionDao().insert(
            csvSession(
                id = "s-gone",
                teaId = null,
                teaName = "Bergoolong",
                category = TeaCategory.OOLONG,
                startedAt = at("2026-10-20T23:30:00", zone = "Asia/Tokyo"),
                endedAt = null,
                status = SessionStatus.MANUAL,
                zoneId = "Asia/Tokyo",
                leafGrams = 5.0,
                costCents = 1_234,
                rating = 3.0,
            ),
        )
        db.sessionDao().insertSteps(listOf(csvStep("s-gone", 0, actualSeconds = 40, tempC = 95, umami = 4, roast = 3)))

        db.sessionDao().insert(
            csvSession(
                id = "s-running",
                teaId = "t-green",
                teaName = "Nebelhang Sencha",
                category = TeaCategory.GREEN,
                startedAt = at("2026-10-24T09:00:00"),
                endedAt = null,
                status = SessionStatus.IN_PROGRESS,
            ),
        )
        db.sessionDao().insertSteps(listOf(csvStep("s-running", 0, actualSeconds = 30, umami = 3)))
    }

    /** A session without tea and without a snapshot Kategorie: the Grüntee set (05 §5), so Röstung stays empty. */
    private suspend fun insertUncategorised() {
        db.sessionDao().insert(
            csvSession(
                id = "s-uncategorised",
                teaId = null,
                teaName = "=Unbekannter Tee",
                category = null,
                startedAt = at("2026-10-19T12:00:00"),
            ),
        )
        db.sessionDao().insertSteps(listOf(csvStep("s-uncategorised", 0, actualSeconds = 30, umami = 2, roast = 4)))
        db.tastingDao().insertDescriptor(csvDescriptor("s-uncategorised", "test.at", 1))
    }

    private companion object {
        const val SESSIONS = 1_000
        val BOM = Char(0xFEFF).toString()
        const val CRLF = "\r\n"
        val NBSP = Char(0xA0).toString()
        const val HEADER = "Datum;Uhrzeit;Tee;Kategorie;Rezept;Gefäß;Blatt (g);Wasser (ml);Aufgüsse;Dauer;" +
            "Bewertung Tee;Bewertung Zubereitung;Kosten (€);Beschreibungen;Notizen;Aufgüsse im Detail;" +
            "Umami;Süße;Bitterkeit;Adstringenz;Körper;Aroma;Röstung/Malz;Säure;Frische;Würze"

        // The Grüntee set's six: Umami 4,5 and Süße 3,0; the stored Röstung 5 stays out (D-130 Q10).
        val GREEN_ROW = "21.10.2026;07:45;Nebelhang Sencha;Grün;Kyusu;Shiboridashi;4,0;120;3 von 4;0:12:30;" +
            "4,5;4;0,36;Nori, Spinat;\"Süß; \"\"weich\"\"\nzweite Zeile\";" +
            "Spülen 0:10 | 60$NBSP°C 1:33 | 65$NBSP°C 0:20 | 70$NBSP°C 0:45;" +
            "4,5;3,0;;;;;;;;"

        // The herbal set: Süße, Säure, Frische; the stored Umami 5 stays out; the skipped step is not listed.
        const val HERBAL_ROW = "22.10.2026;19:05;\"Wiesen \"\"Kräuter\"\"; Mischung\";Kräuter;;;;;1 von 2;0:05:30;" +
            ";;;;;5:00;" +
            ";2,0;;;;;;3,0;4,0;"

        // The tea's Geschmacksprofil (oxidised) beats its Kategorie: Röstung/Malz 2,0, not Umami. A vessel and a
        // note that start like a formula get an apostrophe, then the quoting applies to the result (the PO, #337).
        val OVERRIDE_ROW = "23.10.2026;06:00;Rauchgrün;Grün;;\"'+Glas; klein\";;;1 von 1;0:10:00;" +
            ";;;;'- etwas bitter;80$NBSP°C 1:00;" +
            ";;;;;;2,0;;;"

        // The tea is gone: its snapshot Kategorie Oolong decides; Tokyo's date and time; no end, so no Dauer.
        val GONE_ROW = "20.10.2026;23:30;Bergoolong;Oolong;;;5,0;;1 von 1;;" +
            "3;;12,34;;;95$NBSP°C 0:40;" +
            ";;;;;;3,0;;;"

        // No tea and no Kategorie: an empty cell and the Grüntee set; no temperature at all, so the time alone. The
        // tea name and the descriptor start like a formula, so they get an apostrophe.
        const val UNCATEGORISED_ROW = "19.10.2026;12:00;'=Unbekannter Tee;;;;;;1 von 1;0:10:00;" +
            ";;;'@frisch;;0:30;" +
            "2,0;;;;;;;;;"

        val GOLDEN = BOM +
            listOf(HEADER, OVERRIDE_ROW, HERBAL_ROW, GREEN_ROW, GONE_ROW, UNCATEGORISED_ROW).joinToString("") {
                it +
                    CRLF
            }
    }
}
