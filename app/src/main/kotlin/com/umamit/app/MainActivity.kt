package com.umamit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umamit.app.probe.ProbeScreen
import dagger.hilt.android.AndroidEntryPoint

/** Single activity (06 §2). Placeholder content until the navigation shell (M1-05). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                UmamitRoot()
            }
        }
    }
}

/** Placeholder route switch until the NavHost (M1-05): start screen or the probe screen (M1-03a). */
@Composable
private fun UmamitRoot() {
    var showProbe by rememberSaveable { mutableStateOf(false) }
    if (showProbe) {
        ProbeScreen(onBack = { showProbe = false })
    } else {
        PlaceholderScreen(onOpenProbe = { showProbe = true })
    }
}

@Composable
private fun PlaceholderScreen(onOpenProbe: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.placeholder_welcome),
                style = MaterialTheme.typography.headlineMedium,
            )
            // Throwaway probe entry (M1-03a); removed or hidden before v0.1.0.
            OutlinedButton(onClick = onOpenProbe, modifier = Modifier.padding(top = 32.dp)) {
                Text(stringResource(R.string.probe_entry))
            }
        }
    }
}
