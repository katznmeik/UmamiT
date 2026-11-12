package com.umamit.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

private const val JOURNAL_WAIT_MS = 5_000L

/**
 * Waits until a node matching [matcher] is in the tree. The app builds the Journal's list on `Dispatchers.Default`,
 * which `waitForIdle()` does not wait for (`testJournalSources` builds it in place, #233), so a test that opens the
 * Journal waits for a node of the list before it reads, clicks or asserts on it, and before any negative assert (an
 * unbuilt list shows nothing). Fails on the timeout.
 */
fun ComposeTestRule.awaitNode(matcher: SemanticsMatcher) {
    waitUntil(JOURNAL_WAIT_MS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
}

/** [awaitNode] for a node with exactly [text]. */
fun ComposeTestRule.awaitText(text: String) = awaitNode(hasText(text))

/** [awaitNode] for a node with exactly [description] as its content description. */
fun ComposeTestRule.awaitDescription(description: String) = awaitNode(hasContentDescription(description))
