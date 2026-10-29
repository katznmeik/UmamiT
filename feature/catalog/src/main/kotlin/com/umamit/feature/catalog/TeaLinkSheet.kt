package com.umamit.feature.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme

/** The sheet of a tea from a shop link (SCR-19, CAT-17). */
const val TEA_LINK_SHEET_TAG = "tea_link_sheet"

/** *Link einfügen*'s paste field. */
const val TEA_LINK_FIELD_TAG = "tea_link_field"

/** *Tee laden*. */
const val TEA_LINK_LOAD_TAG = "tea_link_load"

/** A shared link's *Schließen*. */
const val TEA_LINK_CLOSE_TAG = "tea_link_close"

/** The message after a link that led to no tea. */
const val TEA_LINK_MESSAGE_TAG = "tea_link_message"

/**
 * A link shared to the app from the browser's *Teilen* sheet (CAT-17 AC1): its untrusted [text]
 * as shared, and a new [token] per share, so one share is loaded once.
 */
data class TeaLinkShare(val token: Long, val text: String)

/**
 * The tea link's sheet (SCR-19, CAT-17).
 *
 * @property shared a link shared to the app: no field, only the loading state or the message.
 * @property text the paste field's text.
 * @property loading the link's one page is being fetched.
 * @property message why the last link led to no tea; never [TeaLinkOutcome.Opened].
 */
data class TeaLinkSheetState(
    val shared: Boolean = false,
    val text: String = "",
    val loading: Boolean = false,
    val message: TeaLinkOutcome? = null,
) {
    /** *Tee laden* acts: a text to read and nothing loading. */
    val canLoad: Boolean get() = !loading && text.isNotBlank()
}

/**
 * *Link einfügen* (SCR-19, CAT-17, D-094) as Material 3's modal bottom sheet: „Tee per Link
 * hinzufügen", the paste field „Link zur Tee-Seite bei Tee-Kontor Kiel" and *Tee laden*; while it
 * loads, a progress bar and „Tee wird geladen …"; a link that led to no tea leaves the sheet open
 * with the message under the field (03 §5). A shared link ([TeaLinkSheetState.shared]) shows the
 * same sheet without the field, and *Schließen* once there is a message. Its host never composes
 * it during an alert (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeaLinkSheet(
    state: TeaLinkSheetState,
    onTextChange: (String) -> Unit,
    onLoad: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberCatalogSheetState(UmamiTheme.reduceMotion),
        modifier = Modifier.testTag(TEA_LINK_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            Text(
                text = stringResource(R.string.tea_link_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (state.shared) Shared(state, onDismissRequest) else Paste(state, onTextChange, onLoad)
        }
    }
}

@Composable
private fun ColumnScope.Paste(state: TeaLinkSheetState, onTextChange: (String) -> Unit, onLoad: () -> Unit) {
    val message = state.message?.let { stringResource(it.messageRes()) }
    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChange,
        // One line: at a large font scale a wrapped label would cross the field's border; TalkBack reads it whole.
        label = { Text(stringResource(R.string.tea_link_field), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = message?.let { { Message(it) } },
        isError = message != null,
        enabled = !state.loading,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (state.canLoad) onLoad() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TEA_LINK_FIELD_TAG),
    )
    if (state.loading) Loading()
    Spacer(Modifier.height(UmamiSpacing.XSmall))
    PillButton(
        text = stringResource(R.string.tea_link_load),
        onClick = onLoad,
        enabled = state.canLoad,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TEA_LINK_LOAD_TAG),
    )
}

@Composable
private fun ColumnScope.Shared(state: TeaLinkSheetState, onClose: () -> Unit) {
    val message = state.message
    when {
        state.loading -> Loading()

        message != null -> {
            Message(stringResource(message.messageRes()))
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEA_LINK_CLOSE_TAG),
            ) {
                Text(stringResource(R.string.tea_link_close))
            }
        }
    }
}

@Composable
private fun ColumnScope.Loading() {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        text = stringResource(R.string.tea_link_loading),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The message, read out when it appears (TalkBack). */
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .testTag(TEA_LINK_MESSAGE_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The copy of 03 §5 for a link that led to no tea; the shop's refusals as the refresh says them. */
@StringRes
private fun TeaLinkOutcome.messageRes(): Int = when (this) {
    TeaLinkOutcome.NotATeaLink -> R.string.tea_link_not_a_tea_link

    TeaLinkOutcome.NotInCatalog -> R.string.tea_link_not_in_catalog

    TeaLinkOutcome.RefreshRunning -> R.string.tea_link_refresh_running

    TeaLinkOutcome.NoTeaPage -> R.string.tea_link_no_tea_page

    TeaLinkOutcome.Unreadable -> R.string.tea_link_unreadable

    TeaLinkOutcome.Offline -> R.string.catalog_refresh_offline

    TeaLinkOutcome.Blocked -> R.string.catalog_refresh_blocked

    TeaLinkOutcome.ShopUnavailable -> R.string.catalog_refresh_unavailable

    TeaLinkOutcome.StorageFailed -> R.string.catalog_refresh_storage

    // Never a message: the detail opens.
    is TeaLinkOutcome.Opened -> R.string.tea_link_loading
}

/** The clipboard's text, or null; its other content (a file, an image, a stream) is never read. */
internal suspend fun Clipboard.plainText(): String? = try {
    getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
} catch (
    // A clipboard that can't be read (another profile, a policy) leaves the field empty.
    @Suppress("TooGenericExceptionCaught") _: RuntimeException,
) {
    null
}
