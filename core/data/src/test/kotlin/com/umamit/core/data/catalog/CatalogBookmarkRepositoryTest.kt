package com.umamit.core.data.catalog

import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Merkliste (CAT-18, 05 §3.13, D-097; spec #108 seam 4) on `umamit.db` in memory: *Merken* marks a catalog tea
 * by its key, the list shows the marked keys, and the marks are personal data that only the user changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogBookmarkRepositoryTest {
    private val data = TestDatabase()
    private val bookmarks = data.bookmarks

    @After
    fun tearDown() = data.close()

    @Test
    fun `nothing is marked at first`() = runTest {
        assertThat(bookmarks.observeMarked().first()).isEmpty()
        assertThat(bookmarks.isMarked("TG0001")).isFalse()
    }

    @Test
    fun `a marked tea is on the list and reads as marked`() = runTest {
        bookmarks.mark("TG0001")
        bookmarks.mark("TG0002")

        assertThat(bookmarks.observeMarked().first()).containsExactly("TG0001", "TG0002")
        assertThat(bookmarks.isMarked("TG0001")).isTrue()
        assertThat(bookmarks.isMarked("TG0003")).isFalse()
    }

    @Test
    fun `marking a marked tea again keeps one mark`() = runTest {
        bookmarks.mark("TG0001")

        bookmarks.mark("TG0001")

        assertThat(bookmarks.observeMarked().first()).containsExactly("TG0001")
        bookmarks.unmark("TG0001")
        assertThat(bookmarks.isMarked("TG0001")).isFalse()
        assertThat(bookmarks.observeMarked().first()).isEmpty()
    }

    @Test
    fun `unmarking removes only that tea's mark`() = runTest {
        bookmarks.mark("TG0001")
        bookmarks.mark("TG0002")

        bookmarks.unmark("TG0001")

        assertThat(bookmarks.observeMarked().first()).containsExactly("TG0002")
        assertThat(bookmarks.isMarked("TG0001")).isFalse()
        assertThat(bookmarks.isMarked("TG0002")).isTrue()
    }

    @Test
    fun `unmarking a tea without a mark changes nothing`() = runTest {
        bookmarks.mark("TG0002")

        bookmarks.unmark("TG0001")

        assertThat(bookmarks.observeMarked().first()).containsExactly("TG0002")
    }
}
