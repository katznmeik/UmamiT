package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A received recipe file through the real root and shell (PRF-08, SCR-32): from Start the shell turns to Sammlung
 * with SCR-32 on top, which gets the file's bytes; *Rezept übernehmen* replaces it with the tea's detail and
 * „Rezept „Kyusu" übernommen"; ✕ returns to the Sammlung root and lets the bytes go. SCR-32 and the detail are
 * stand-ins here (their own tests cover them).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ReceiveRecipeFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val sessions = FakeSessions(collection, FakeSettingsRepository())
    private val files = ReceivedRecipeFiles(newToken = { 42L })
    private val bytes = byteArrayOf(7, 8, 9)
    private var received: ByteArray? = null

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = NoActiveBrewSource,
                    journalSources = testJournalSources(sessions, collection.teas),
                    alertKey = MutableStateFlow(null),
                    receivedRecipes = files,
                    collectionScreens = CollectionStackScreens(
                        tea = { host ->
                            Column {
                                Text(DETAIL)
                                TextButton(onClick = host.onBack) { Text("Zurück") }
                            }
                        },
                        receiveRecipe = { host ->
                            received = host.bytes
                            Column {
                                Text(SCR32)
                                TextButton(onClick = host.onClose) { Text(CLOSE) }
                                TextButton(onClick = { host.onTakenOver("t1", "Kyusu") }) { Text(TAKE_OVER) }
                            }
                        },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aReceivedFileOpensRezeptUebernehmenInSammlungAndATakeOverOpensTheDetail() {
        show()
        files.receive(bytes)
        compose.waitForIdle()

        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        compose.onNodeWithText(SCR32).assertExists()
        assertThat(received).isEqualTo(bytes)
        assertThat(files.pending.value).isNull()

        compose.onNodeWithText(TAKE_OVER).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(DETAIL).assertExists()
        compose.onNodeWithText(SCR32).assertDoesNotExist()
        compose.onNodeWithText("Rezept „Kyusu\" übernommen").assertExists()
        assertThat(files.bytes(42L)).isNull()
    }

    @Test
    fun closeReturnsToSammlungAndLetsTheBytesGo() {
        show()
        files.receive(bytes)
        compose.waitForIdle()

        compose.onNodeWithText(CLOSE).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(SCR32).assertDoesNotExist()
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        assertThat(files.bytes(42L)).isNull()
    }

    private companion object {
        const val SCR32 = "Rezept-übernehmen-Platzhalter"
        const val DETAIL = "Tee-Detail-Platzhalter"
        const val CLOSE = "Platzhalter schließen"
        const val TAKE_OVER = "Platzhalter übernehmen"
    }
}
