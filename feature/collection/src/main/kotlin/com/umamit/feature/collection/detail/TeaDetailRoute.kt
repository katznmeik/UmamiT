package com.umamit.feature.collection.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.feature.collection.R

/**
 * The tea detail with its view model (SCR-09). Its argument is the view model's `teaId`
 * ([TeaDetailViewModel.TEA_ID]). *Beim Tee-Kontor nachbestellen* hands the shop page to the browser: the user's
 * own navigation, the app sends no request (INV-09 AC3, GEN-01).
 */
@Composable
fun TeaDetailRoute(
    host: TeaDetailHost,
    modifier: Modifier = Modifier,
    viewModel: TeaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is TeaDetailEvent.Archived -> currentHost.onArchived(ArchivedTea(event.teaId, event.teaName))
                TeaDetailEvent.Deleted -> currentHost.onDeleted()
            }
        }
    }
    TeaDetailScreen(
        state = state,
        actions = TeaDetailActions(
            back = host.onBack,
            brew = host.onBrew,
            editTea = host.onEditTea,
            editRecipe = host.onEditRecipe,
            addRecipe = host.onAddRecipe,
            toggleFavorite = viewModel::toggleFavorite,
            archive = viewModel::archive,
            restore = viewModel::restore,
            delete = viewModel::delete,
            correctStock = viewModel::correctStock,
            openShop = context::openInBrowser,
        ),
        modifier = modifier,
        alertKey = host.alertKey,
    )
}

/** Opens [url] in the browser; without one, says so. */
private fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.collection_tea_no_browser, Toast.LENGTH_SHORT).show()
    }
}
