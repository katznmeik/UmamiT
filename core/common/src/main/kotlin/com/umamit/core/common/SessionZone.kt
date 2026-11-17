package com.umamit.core.common

import java.time.DateTimeException
import java.time.ZoneId

/** A session's recorded zone; one this device doesn't know counts as the device's (as in the repository, D-054). */
fun sessionZone(id: String): ZoneId = try {
    ZoneId.of(id)
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
