package com.umamit.feature.brew.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The setup route (SCR-04). Its arguments are the view model's `teaId` and
 * `sessionId` ([BrewSetupViewModel.TEA_ID], [BrewSetupViewModel.SESSION_ID]).
 * [onOpenTimer] runs once the session waits in READY (TMR-01).
 */
@Composable
fun BrewSetupRoute(
    onBack: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrewSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenTimer by rememberUpdatedState(onOpenTimer)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BrewSetupEvent.OpenTimer -> currentOnOpenTimer()
            }
        }
    }
    val actions = remember(viewModel, onBack) {
        BrewSetupActions(
            back = onBack,
            selectRecipe = viewModel::selectRecipe,
            selectVessel = viewModel::selectVessel,
            stepWater = viewModel::stepWater,
            stepLeaf = viewModel::stepLeaf,
            setRatioLocked = viewModel::setRatioLocked,
            applySteps = viewModel::applySteps,
            saveStepsToRecipe = viewModel::saveStepsToRecipe,
            saveToRecipe = viewModel::saveToRecipe,
            start = viewModel::startBrew,
            confirmRestart = viewModel::confirmRestart,
            dismissQuestion = viewModel::dismissQuestion,
            correctStock = viewModel::correctStock,
        )
    }
    BrewSetupScreen(state = state, modifier = modifier, actions = actions)
}
