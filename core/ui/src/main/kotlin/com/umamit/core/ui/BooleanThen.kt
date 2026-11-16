package com.umamit.core.ui

/** [value] when this is true, else null: an editor field's error message for a failed check. */
fun Boolean.then(value: Int): Int? = if (this) value else null
