package com.umamit.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

// DELIBERATELY FAILING (CI gate proof, M1-02 #11). Reverted right after.
class GateProbeTest {
    @Test
    fun failsOnPurpose() {
        assertEquals(1, 2)
    }
}
