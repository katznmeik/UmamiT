package com.umamit.feature.collection.receive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** SCR-32 with its view model. */
@Composable
fun ReceiveRecipeRoute(
    host: ReceiveRecipeHost,
    modifier: Modifier = Modifier,
    viewModel: ReceiveRecipeViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel, host.token) { viewModel.open(host.token, host.bytes) }
    val currentHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is ReceiveRecipeEvent.TakenOver -> currentHost.onTakenOver(event.teaId, event.recipeName)
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReceiveRecipeScreen(
        state = state,
        actions = ReceiveRecipeActions(
            close = host.onClose,
            choose = viewModel::choose,
            pick = viewModel::pick,
            loadShopLink = viewModel::loadShopLink,
            takeOver = viewModel::takeOver,
            linkMessage = host.linkMessage,
            savePackage = viewModel::savePackage,
            dismissPackageSheet = viewModel::dismissPackageSheet,
            closePackageSheet = viewModel::closePackageSheet,
        ),
        modifier = modifier,
        alertKey = host.alertKey,
        packageSheet = host.packageSheet,
    )
}
