package com.umamit.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Test seam 4 of the M1 plan: version → versionCode per 06 §6. */
class VersionCodeTest {

    @Test
    fun `probe release maps to N = 1`() {
        assertEquals(101, versionCodeOf("0.0.1-probe"))
        assertEquals(201, versionCodeOf("0.0.2-probe"))
        assertEquals(301, versionCodeOf("0.0.3-probe"))
    }

    @Test
    fun `release candidates map to their number`() {
        assertEquals(10001, versionCodeOf("0.1.0-rc.1"))
        assertEquals(10002, versionCodeOf("0.1.0-rc.2"))
        assertEquals(10098, versionCodeOf("0.1.0-rc.98"))
    }

    @Test
    fun `final releases map to N = 99`() {
        assertEquals(10099, versionCodeOf("0.1.0"))
        assertEquals(1000099, versionCodeOf("1.0.0"))
        assertEquals(2030499, versionCodeOf("2.3.4"))
    }

    @Test
    fun `every later version has a higher versionCode`() {
        val ordered = listOf(
            "0.0.1-probe", "0.0.2-probe", "0.1.0-rc.1", "0.1.0-rc.2", "0.1.0-rc.98", "0.1.0",
            "0.1.1-rc.1", "0.1.1", "0.2.0", "0.99.99", "1.0.0-rc.1", "1.0.0", "10.0.0",
        )
        val codes = ordered.map(::versionCodeOf)
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `malformed versions are rejected`() {
        listOf(
            "0.1",
            "0.1.0-rc.0",
            "0.1.0-rc.99",
            "0.1.0-beta",
            "",
            "1",
            "1.0.0.0",
            "v1.0.0",
            "1.0.0-rc",
            "1.0.0-rc.",
            "1.0.0-rc.01",
            "1.0.0-RC.1",
            "1.0.0-probe.1",
            "01.0.0",
            "1.00.0",
            "1.0.0 ",
            "0.100.0",
            "0.1.100",
            "2101.0.0",
        ).forEach { version ->
            assertThrows("'$version' must be rejected", IllegalArgumentException::class.java) {
                versionCodeOf(version)
            }
        }
    }
}
