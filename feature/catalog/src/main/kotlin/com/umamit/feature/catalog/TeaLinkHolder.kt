package com.umamit.feature.catalog

import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.core.data.catalog.TeaLinkOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A tea from a shop link (CAT-17): its [sheet] while open, and the catalog key of a tea a link
 * [opened] (stored, refreshed or found), until its detail (SCR-21) is shown, with the [notice] why
 * its page didn't refresh it, if so (CAT-17 AC6, D-096), shown as the detail opens.
 */
data class TeaLinkState(
    val sheet: TeaLinkSheetState? = null,
    val opened: String? = null,
    val notice: TeaLinkOutcome? = null,
)

/**
 * The *Link einfügen* sheet's state (CAT-17, D-124): a tea from a pasted or shared shop link, only on the
 * user's action and one at a time. Its loads run in [scope] (the catalog view model's), so the job and the
 * last share live exactly as long as that view model.
 */
internal class TeaLinkHolder(private val links: TeaLinkLoader, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(TeaLinkState())

    /** The tea link's sheet and the tea to open (CAT-17). */
    val state: StateFlow<TeaLinkState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var lastShare: Long? = null

    /** *Link einfügen*: the paste sheet, empty (CAT-17 AC1). */
    fun open() {
        job?.cancel()
        mutableState.update { it.copy(sheet = TeaLinkSheetState()) }
    }

    /**
     * The clipboard's [text]: when it holds a tea link and the paste field is still empty, the
     * field shows that link (SCR-19). Anything else on the clipboard is never shown.
     */
    fun fillFromClipboard(text: String?) {
        val link = text?.let(links::linkIn) ?: return
        mutableState.update { state ->
            val sheet = state.sheet?.takeIf { !it.shared && !it.loading && it.text.isEmpty() } ?: return@update state
            state.copy(sheet = sheet.copy(text = link))
        }
    }

    /** The paste field changed: its message goes. */
    fun changeText(text: String) = mutableState.update { state ->
        val sheet = state.sheet?.takeIf { !it.loading } ?: return@update state
        state.copy(sheet = sheet.copy(text = text, message = null))
    }

    /** *Tee laden*: the pasted link. */
    fun load() {
        val sheet = mutableState.value.sheet?.takeIf { !it.shared && it.canLoad } ?: return
        load(sheet.text, sheet)
    }

    /** A link shared to the app (CAT-17 AC1): loaded at once, each [share] once. */
    fun loadShared(share: TeaLinkShare) {
        if (share.token == lastShare) return
        lastShare = share.token
        load(share.text, TeaLinkSheetState(shared = true))
    }

    /** The sheet was closed: a link still loading is cancelled. */
    fun dismiss() {
        job?.cancel()
        job = null
        mutableState.update { it.copy(sheet = null) }
    }

    /** The detail of the tea a link opened is shown. */
    fun teaOpened() = mutableState.update { it.copy(opened = null, notice = null) }

    /** One link at a time: a link still loading is cancelled, and ends, before [text] is loaded. */
    private fun load(text: String, sheet: TeaLinkSheetState) {
        val previous = job
        mutableState.update { it.copy(sheet = sheet.copy(loading = true, message = null)) }
        job = scope.launch {
            previous?.cancelAndJoin()
            val outcome = links.load(text)
            mutableState.update { state ->
                when (outcome) {
                    is TeaLinkOutcome.Opened ->
                        TeaLinkState(sheet = null, opened = outcome.key, notice = outcome.notRefreshed)

                    else -> state.copy(sheet = state.sheet?.copy(loading = false, message = outcome))
                }
            }
        }
    }
}
