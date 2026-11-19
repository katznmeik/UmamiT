package com.umamit.core.backup.restore

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

/**
 * [Durable.deleteTree] (#349): a tree, a single file or nothing at all; a file or folder that vanishes while it walks
 * counts as deleted (two deletes of one tree at once both succeed); a link is deleted, never followed.
 */
class DurableDeleteTreeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun tree(root: File): File = root.apply {
        repeat(DIRS) { d ->
            val dir = File(this, "d$d").apply { mkdirs() }
            repeat(FILES) { f -> File(dir, "f$f.jpg").writeBytes(byteArrayOf(f.toByte())) }
        }
        File(this, "top.json").writeText("{}")
    }

    @Test
    fun deletesATreeAFileAndNothing() {
        val root = tree(File(folder.root, "photos.new"))
        val file = File(folder.root, "umamit-safety.new").apply { writeBytes(byteArrayOf(7)) }

        Durable.deleteTree(root)
        Durable.deleteTree(file)
        Durable.deleteTree(File(folder.root, "missing"))

        assertThat(root.exists()).isFalse()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun twoDeletesOfOneTreeAtOnceBothSucceed() {
        repeat(ROUNDS) { round ->
            val root = tree(File(folder.root, "photos.new$round"))
            val file = File(folder.root, "umamit-safety$round.new").apply { writeBytes(byteArrayOf(7)) }
            val start = CyclicBarrier(DELETERS)
            val failures = mutableListOf<Throwable>()

            List(DELETERS) {
                thread {
                    start.await()
                    try {
                        Durable.deleteTree(root)
                        Durable.deleteTree(file)
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Throwable, // the AssertionError of #349 included
                    ) {
                        synchronized(failures) { failures += e }
                    }
                }
            }.forEach(Thread::join)

            assertThat(failures).isEmpty()
            assertThat(root.exists()).isFalse()
            assertThat(file.exists()).isFalse()
        }
    }

    @Test
    fun aLinkIsDeletedNeverFollowed() {
        val outside = File(folder.root, "photos").apply { mkdirs() }
        val ownPhoto = File(outside, "own.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val root = File(folder.root, "photos.old").apply { mkdirs() }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())

        Durable.deleteTree(root)

        assertThat(root.exists()).isFalse()
        assertThat(ownPhoto.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    private companion object {
        const val DIRS = 8
        const val FILES = 25
        const val ROUNDS = 20
        const val DELETERS = 3
    }
}
