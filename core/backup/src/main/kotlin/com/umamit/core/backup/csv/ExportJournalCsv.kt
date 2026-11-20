package com.umamit.core.backup.csv

import com.umamit.core.database.UmamiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

/**
 * *Journal als CSV* (BAK-04, D-130 Q9, Q10, 06 §2.2): the finished sessions as [JournalCsv] to the picker's document,
 * UTF-8 with a byte order mark, so German Excel reads the umlauts. Export only; it reads the journal's rows in one
 * transaction and never touches a setting, so „Letzte Sicherung" stays (D-130 Q12).
 */
class ExportJournalCsv @Inject constructor(private val db: UmamiDatabase) {
    /**
     * Writes the CSV to [output] with [labels] and returns how many sessions it holds. Throws what the read or the
     * write throws. [output] is flushed, not closed.
     */
    suspend operator fun invoke(output: OutputStream, labels: JournalCsvLabels): Int = withContext(Dispatchers.IO) {
        val sessions = db.journalDao().observeSessions().first()
        val writer = OutputStreamWriter(output, Charsets.UTF_8).buffered(BUFFER_CHARS)
        writer.write(BYTE_ORDER_MARK)
        val count = JournalCsv.write(sessions, labels, writer)
        writer.flush()
        count
    }

    private companion object {
        const val BYTE_ORDER_MARK = 0xFEFF
        const val BUFFER_CHARS = 64 * 1_024
    }
}
