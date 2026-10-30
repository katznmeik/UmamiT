package com.umamit.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.timer.reliability.startFirstIntent

/**
 * Start (SCR-02) with its view model. *Nochmal brühen* opens the timer once
 * the session waits in READY ([onOpenTimer]); the other taps go to the host
 * through [actions], whose *Nochmal brühen* and question callbacks the route
 * replaces with the view model's.
 */
@Composable
fun HomeRoute(
    actions: HomeActions,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenTimer by rememberUpdatedState(onOpenTimer)
    // The user may come back from the system settings (TMR-31).
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenTimer -> currentOnOpenTimer()
            }
        }
    }
    val routeActions = remember(actions, viewModel) {
        HomeActions(
            openSettings = actions.openSettings,
            startBrew = actions.startBrew,
            openTimer = actions.openTimer,
            quickTimer = actions.quickTimer,
            brewAgain = viewModel::brewAgain,
            openSetup = actions.openSetup,
            showJournal = actions.showJournal,
            openCatalog = actions.openCatalog,
            confirmRestart = viewModel::confirmRestart,
            dismissQuestion = viewModel::dismissQuestion,
            fixReliability = { context.startFirstIntent(viewModel.fixReliability()) },
            reorder = context::openShopPage,
            hideLowStock = viewModel::hideLowStock,
        )
    }
    HomeScreen(
        state = state,
        nowElapsedMs = remember(viewModel) { viewModel::nowElapsedMs },
        modifier = modifier,
        actions = routeActions,
        scrollToTopRequest = scrollToTopRequest,
    )
}

/**
 * *Nachbestellen* (INV-10): INV-09's shop page in the browser. The browser loads it, not the app (INV-09
 * AC3, GEN-01).
 */
private fun Context.openShopPage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.home_low_stock_no_browser, Toast.LENGTH_SHORT).show()
    }
}
