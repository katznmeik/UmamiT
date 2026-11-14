package com.umamit.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which left tabs stay composed (A-10): only those the user left with the tab bar or back. */
class TabKeeperTest {
    @Test
    fun `a user switch keeps the tab it leaves, an app switch drops it`() {
        val keeper = TabKeeper(Tab.Start)
        keeper.keepLeaving()
        keeper.observe(Tab.Journal)
        assertThat(keeper.kept).containsExactly(Tab.Start)

        // The app switches (Alle anzeigen, a notification): the Journal it leaves is disposed after the fade.
        keeper.observe(Tab.Collection)
        assertThat(keeper.kept).containsExactly(Tab.Start)

        // Back to a kept tab: it is on screen, no longer a hidden one.
        keeper.keepLeaving()
        keeper.observe(Tab.Start)
        assertThat(keeper.kept).containsExactly(Tab.Collection)
    }

    @Test
    fun `a cancelled back leaves no keep for the next app switch`() {
        val keeper = TabKeeper(Tab.Journal)
        // Predictive back seeks to Start, then is cancelled: the selection snaps back.
        keeper.keepLeaving()
        keeper.observe(Tab.Start)
        keeper.observe(Tab.Journal)
        assertThat(keeper.kept).doesNotContain(Tab.Journal)

        keeper.observe(Tab.Collection)
        assertThat(keeper.kept).doesNotContain(Tab.Journal)
    }

    @Test
    fun `the same selection changes nothing`() {
        val keeper = TabKeeper(Tab.Start)
        keeper.keepLeaving()
        keeper.observe(Tab.Start)
        assertThat(keeper.kept).isEmpty()
    }
}
