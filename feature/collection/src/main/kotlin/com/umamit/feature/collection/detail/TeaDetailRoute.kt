package com.umamit.feature.collection.detail

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.ui.PhotoPicker
import com.umamit.core.ui.SystemPhotoPicker
import com.umamit.core.ui.openInBrowser
import com.umamit.core.ui.rememberPhotoPicker
import com.umamit.feature.collection.R
import kotlinx.coroutines.launch

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
    val snackbar = remember { SnackbarHostState() }
    val purchaseEdited = stringResource(R.string.collection_purchase_edited)
    val correctionEdited = stringResource(R.string.collection_correction_edited)
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

                // In its own coroutine: a snackbar on screen never holds back the next event.
                TeaDetailEvent.PurchaseEdited -> launch { snackbar.showSnackbar(purchaseEdited) }

                TeaDetailEvent.CorrectionEdited -> launch { snackbar.showSnackbar(correctionEdited) }
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
            openShop = { context.openInBrowser(it, R.string.collection_tea_no_browser) },
            addPhoto = pickPhoto,
            deleteStockEntry = viewModel::deleteStockEntry,
            updatePurchase = viewModel::updatePurchase,
            updateCorrection = viewModel::updateCorrection,
            openSession = host.onOpenSession,
            setCoverPhoto = viewModel::setCoverPhoto,
            deletePhoto = viewModel::deletePhoto,
            resetFromCatalog = viewModel::resetFromCatalog,
            setStockWarning = viewModel::setStockWarning,
            setLowStockGrams = viewModel::setLowStockGrams,
            showAllSessions = host.onShowAllSessions,
            openCatalog = host.onOpenCatalog,
            openHelp = host.onOpenHelp,
        ),
        modifier = modifier,
        alertKey = host.alertKey,
        snackbar = snackbar,
    )
}

private fun Context.toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
