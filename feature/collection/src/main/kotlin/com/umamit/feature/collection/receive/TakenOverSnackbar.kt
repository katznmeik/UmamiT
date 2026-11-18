package com.umamit.feature.collection.receive

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.feature.collection.R

/** A recipe just taken over on SCR-32, for „Rezept „Kyusu" übernommen" on the tea detail; [token] tells two apart. */
data class TakenOverRecipe(val recipeName: String, val token: Long = System.nanoTime()) {
    companion object {
        /** Keeps the notice across a configuration change and process death. */
        val Saver: Saver<TakenOverRecipe?, Any> = listSaver(
            save = { notice -> notice?.let { listOf(it.recipeName, it.token.toString()) }.orEmpty() },
            restore = { list -> if (list.size == 2) TakenOverRecipe(list[0], list[1].toLong()) else null },
        )
    }
}

/** „Rezept „Kyusu" übernommen" (03 SCR-32) over the tea detail SCR-32 opened; [onDone] clears it once it is gone. */
@Composable
fun TakenOverSnackbar(notice: TakenOverRecipe?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    val message = notice?.let { stringResource(R.string.receive_recipe_taken_over, it.recipeName) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(notice) {
        if (message == null) return@LaunchedEffect
        hostState.showSnackbar(message)
        currentOnDone()
    }
    SnackbarHost(hostState = hostState, modifier = modifier)
}
