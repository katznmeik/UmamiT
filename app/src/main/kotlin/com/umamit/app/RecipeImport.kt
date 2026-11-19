package com.umamit.app

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.umamit.feature.collection.R as CollectionR

/**
 * *Meine Tees*' ⋮ *Rezept importieren* (PRF-08 way (c), D-130 Q14): the system file picker (`ACTION_OPEN_DOCUMENT`,
 * any type; the reader decides), its file copied off the main thread as [ReceiveRecipeActivity.copy] copies it (at
 * most 64 KB + 1 byte, a `content://` URI only) into [files], which shows SCR-32. No permission: the picker grants
 * the one URI. Nothing is written.
 */
@Composable
internal fun rememberRecipeImport(files: ReceivedRecipeFiles): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = context.applicationContext.contentResolver
            scope.launch { files.receive(withContext(Dispatchers.IO) { ReceiveRecipeActivity.copy(resolver, uri) }) }
        }
    }
    return {
        try {
            launcher.launch(arrayOf(ANY_TYPE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, CollectionR.string.receive_recipe_no_picker, Toast.LENGTH_SHORT).show()
        }
    }
}

private const val ANY_TYPE = "*/*"

/**
 * The token of the received recipe file the shell has not shown yet (PRF-08), 0 for none; [onReceive] runs once
 * per new one, while the shell is shown (rule 8: a covered shell waits).
 */
@Composable
internal fun pendingRecipeToken(files: ReceivedRecipeFiles, onReceive: () -> Unit): Long {
    val token = files.pending.collectAsStateWithLifecycle().value?.token ?: 0L
    val currentOnReceive by rememberUpdatedState(onReceive)
    LaunchedEffect(token) { if (token != 0L) currentOnReceive() }
    return token
}
