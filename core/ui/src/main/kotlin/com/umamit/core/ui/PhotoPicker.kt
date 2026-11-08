package com.umamit.core.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * The photo picker port (TEA-05, 06 §5.2): one image, or null when the user backs out. The app uses
 * [SystemPhotoPicker]; tests pass a fake to the route, since the system picker can't run there.
 */
abstract class PhotoPicker : ActivityResultContract<Unit, Uri?>()

/**
 * Android's photo picker (06 §5.2): one image at a time, no camera, and no storage permission (the picker grants
 * the one image it returns).
 */
object SystemPhotoPicker : PhotoPicker() {
    private val pick = ActivityResultContracts.PickVisualMedia()

    override fun createIntent(context: Context, input: Unit): Intent =
        pick.createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = pick.parseResult(resultCode, intent)
}

/** Opens [picker] when called; [onPick] gets the picked image's URI, nothing when the user backs out. */
@Composable
fun rememberPhotoPicker(picker: PhotoPicker, onPick: (uri: String) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPick)
    val launcher = rememberLauncherForActivityResult(picker) { uri -> uri?.let { current(it.toString()) } }
    return { launcher.launch(Unit) }
}
