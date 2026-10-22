package com.umamit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.umamit.core.designsystem.theme.UmamiTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity (06 §2): edge-to-edge, hosts the navigation shell. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UmamiTheme {
                // M1-09 binds the timer session as the source of the active brew bar.
                UmamitApp(activeBrewSource = NoActiveBrewSource)
            }
        }
    }
}
