package com.umamit.core.timer.controller

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The device's monotonic clock and boot count (04 §5, §7.2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidMonotonicClockTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reads elapsedRealtime, which keeps counting through deep sleep`() {
        val clock = AndroidMonotonicClock(context)
        val before = clock.elapsedMs()

        ShadowSystemClock.advanceBy(Duration.ofSeconds(90))

        assertThat(clock.elapsedMs() - before).isEqualTo(90_000)
    }

    @Test
    fun `reads the boot count from the global settings`() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 17)

        assertThat(AndroidMonotonicClock(context).bootCount()).isEqualTo(17)
    }
}
