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
import com.umamit.core.ui.PhotoPicker
import com.umamit.core.ui.SystemPhotoPicker
import com.umamit.core.ui.rememberPhotoPicker
import com.umamit.feature.collection.R

/**
 * The tea detail with its view model (SCR-09). Its argument is the view model's `teaId`
 * ([TeaDetailViewModel.TEA_ID]). *Beim Tee-Kontor nachbestellen* hands the shop page to the browser: the user's
 * own navigation, the app sends no request (INV-09 AC3, GEN-01). *+ Foto* opens [photoPicker] (the system's,
 * 06 §5.2; a fake in tests); a photo that can't be added is said in a short toast.
 */
@Composable
fun TeaDetailRoute(
    host: TeaDetailHost,
    modifier: Modifier = Modifier,
    viewModel: TeaDetailViewModel = hiltViewModel(),
    photoPicker: PhotoPicker = SystemPhotoPicker,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is TeaDetailEvent.Archived -> currentHost.onArchived(ArchivedTea(event.teaId, event.teaName))
                TeaDetailEvent.Deleted -> currentHost.onDeleted()
                TeaDetailEvent.PhotoUnreadable -> context.toast(R.string.collection_tea_photo_unreadable)
                TeaDetailEvent.PhotoLimitReached -> context.toast(R.string.collection_tea_photo_limit)
                TeaDetailEvent.PhotoFailed -> context.toast(R.string.collection_tea_photo_failed)
                TeaDetailEvent.PhotoChangeFailed -> context.toast(R.string.collection_tea_photo_change_failed)
                TeaDetailEvent.StockChangeFailed -> context.toast(R.string.collection_tea_stock_change_failed)
                TeaDetailEvent.ResetFailed -> context.toast(R.string.collection_tea_reset_failed)
            }
        }
    }
    val pickPhoto = rememberPhotoPicker(photoPicker, viewModel::addPhoto)
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
            recordPurchase = viewModel::recordPurchase,
            openShop = context::openInBrowser,
            addPhoto = pickPhoto,
            deleteStockEntry = viewModel::deleteStockEntry,
            openSession = host.onOpenSession,
            setCoverPhoto = viewModel::setCoverPhoto,
            deletePhoto = viewModel::deletePhoto,
            resetFromCatalog = viewModel::resetFromCatalog,
            setStockWarning = viewModel::setStockWarning,
            setLowStockGrams = viewModel::setLowStockGrams,
        ),
        modifier = modifier,
        alertKey = host.alertKey,
    )
}

private fun Context.toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Opens [url] in the browser; without one, says so. */
private fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        toast(R.string.collection_tea_no_browser)
    }
}
