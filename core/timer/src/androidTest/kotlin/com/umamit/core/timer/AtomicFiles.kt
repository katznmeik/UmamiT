package com.umamit.core.timer

import java.io.File

/**
 * Writes [text] so that a test in the other process never reads the file half-written: it
 * goes to a temporary file first and is renamed into place. `writeText` creates the file
 * before it writes, so a reader polling `exists()` could read it empty (CI on 98634b3).
 */
internal fun File.writeTextAtomically(text: String) {
    val partial = File(parentFile, "$name.partial")
    partial.writeText(text)
    check(partial.renameTo(this)) { "could not rename $partial to $this" }
}
