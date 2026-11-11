package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.umamit.feature.journal.JournalTeaRequest
import com.umamit.feature.journal.session.SessionPageHost
import com.umamit.feature.journal.session.SessionPageRoute

/**
 * The screens of the Journal stack beyond the list: a past session's page (SCR-16, D-100). The app uses the real
 * route; tests replace it.
 */
internal class JournalStackScreens(val session: @Composable (SessionPageHost) -> Unit = { SessionPageRoute(it) })

/**
 * What the Journal tab's screens get from the shell: the [screens] and [openSetupFromSession], *So nochmal brühen*
 * (BRW-06): the setup prefilled with a past session above the shell, or the alerting timer during an alert (rule 8).
 * [teaRequest] filters the list to a tea (the tea detail's *Alle anzeigen*, TEA-13), null for none.
 */
internal class JournalWiring(
    val screens: JournalStackScreens,
    val openSetupFromSession: (sessionId: String) -> Unit,
    val teaRequest: JournalTeaRequest? = null,
)

/**
 * The tea detail's *Alle anzeigen* requests (TEA-13): [ask] makes a new request for a tea, [current] is the latest one
 * (null before the first). Every ask has a higher token, so the Journal tab applies it again for the same tea after
 * the user cleared its chip. Saved with the shell, so a rotation does not apply the last request a second time.
 */
@Stable
internal class JournalTeaRequests(key: String? = null, token: Int = 0) {
    private var teaKey by mutableStateOf(key)
    private var token by mutableIntStateOf(token)

    val current: JournalTeaRequest? get() = teaKey?.let { JournalTeaRequest(it, token) }

    fun ask(teaId: String) {
        teaKey = teaId
        token++
    }

    companion object {
        val Saver = listSaver<JournalTeaRequests, Any>(
            save = { listOf(it.teaKey.orEmpty(), it.token) },
            restore = { JournalTeaRequests((it[0] as String).ifEmpty { null }, it[1] as Int) },
        )
    }
}
