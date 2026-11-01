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
 * [onOpenTimer] runs once the session waits in READY (TMR-01); [onOpenCatalog] opens the tea's
 * catalog page (SCR-21) by its catalog key, from ⋮ *Im Katalog ansehen* (D-098); [onOpenTea] the tea's detail
 * (SCR-09) by its id, from ⋮ *Tee ansehen* (D-100).
 *
 * *Neues Gefäß …* calls [onNewVessel] (the vessel editor, SCR-14, above the setup). The vessel saved there comes
 * back as [newVesselId]: the setup selects it like any vessel choice (WARE-02, WARE-03) and calls
 * [onConsumeNewVessel], so the host forgets it.
 */
@Composable
fun BrewSetupRoute(
    onBack: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (String) -> Unit = {},
    onOpenTea: (String) -> Unit = {},
    onNewVessel: () -> Unit = {},
    newVesselId: String? = null,
    onConsumeNewVessel: () -> Unit = {},
    viewModel: BrewSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnConsumeNewVessel by rememberUpdatedState(onConsumeNewVessel)
    LaunchedEffect(viewModel, newVesselId) {
        newVesselId?.let {
            viewModel.selectVessel(it)
            currentOnConsumeNewVessel()
        }
    }
    val currentOnOpenTimer by rememberUpdatedState(onOpenTimer)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BrewSetupEvent.OpenTimer -> currentOnOpenTimer()
            }
        }
    }
    val currentOnOpenCatalog by rememberUpdatedState(onOpenCatalog)
    val currentOnOpenTea by rememberUpdatedState(onOpenTea)
    val currentOnNewVessel by rememberUpdatedState(onNewVessel)
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
            openCatalog = { key -> currentOnOpenCatalog(key) },
            openTea = { teaId -> currentOnOpenTea(teaId) },
            newVessel = { currentOnNewVessel() },
        )
    }
    BrewSetupScreen(state = state, modifier = modifier, actions = actions)
}
