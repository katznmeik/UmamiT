package com.umamit.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running brew as the shell needs it for the active brew bar (GEN-06).
 * A placeholder shape: M1-09 replaces it with the real session state.
 */
data class ActiveBrew(val teaName: String)

/** Where the shell reads the active brew from; M1-09 binds the timer session here. */
interface ActiveBrewSource {
    /** The running brew, or `null` when none runs (the bar is then not shown). */
    val activeBrew: StateFlow<ActiveBrew?>
}

/** No brew runs: the source until M1-09. */
object NoActiveBrewSource : ActiveBrewSource {
    override val activeBrew: StateFlow<ActiveBrew?> = MutableStateFlow<ActiveBrew?>(null).asStateFlow()
}

internal const val ACTIVE_BREW_BAR_TAG = "active_brew_bar"

/**
 * Slot above the navigation bar for the active brew bar (03 §2.2, GEN-06).
 * Placeholder content until the `ActiveBrewBar` component (M1-09) fills it; the
 * arrival and departure already use the bar's motion token (03 §3.6).
 */
@Composable
internal fun ActiveBrewBarSlot(activeBrew: ActiveBrew?, modifier: Modifier = Modifier) {
    val motion = UmamiTheme.motion
    AnimatedVisibility(
        visible = activeBrew != null,
        modifier = modifier,
        enter = expandVertically(motion.activeBrewBar()) + fadeIn(motion.fade()),
        exit = shrinkVertically(motion.activeBrewBar()) + fadeOut(motion.exit()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ACTIVE_BREW_BAR_TAG),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Small)) {
                Text(stringResource(R.string.active_brew_running, activeBrew?.teaName.orEmpty()))
            }
        }
    }
}
