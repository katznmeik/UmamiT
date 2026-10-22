package com.umamit.core.timer.controller

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.umamit.core.common.MonotonicClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The device's monotonic clock (04 §5): `SystemClock.elapsedRealtime()`,
 * which keeps counting in deep sleep and ignores wall-clock and zone changes
 * (E1), with `Settings.Global.BOOT_COUNT`, which tells elapsed values of
 * different boots apart (04 §7.2). A reboot ends every process, so the boot
 * count is read once.
 */
class AndroidMonotonicClock @Inject constructor(@ApplicationContext context: Context) : MonotonicClock {
    private val bootCount: Int by lazy {
        Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = bootCount
}
