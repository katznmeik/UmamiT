package com.umamit.core.designsystem.motion

import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemReduceMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    private fun setAnimatorDurationScale(scale: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    @Test
    fun `theme reads reduce motion from the system animator duration scale`() {
        setAnimatorDurationScale(0f)
        var reduceMotion: Boolean? = null
        compose.setContent { UmamiTheme { reduceMotion = UmamiTheme.reduceMotion } }

        compose.runOnIdle { assertThat(reduceMotion).isTrue() }
    }

    @Test
    fun `theme follows a change of the system setting while running`() {
        setAnimatorDurationScale(1f)
        var motion: UmamiMotion? = null
        compose.setContent { UmamiTheme { motion = UmamiTheme.motion } }
        compose.runOnIdle { assertThat(motion?.reduceMotion).isFalse() }

        setAnimatorDurationScale(0f)

        compose.runOnIdle { assertThat(motion?.reduceMotion).isTrue() }
    }

    @Test
    fun `tests can force reduce motion regardless of the system setting`() {
        setAnimatorDurationScale(1f)
        var reduceMotion: Boolean? = null
        compose.setContent { UmamiTheme(reduceMotion = true) { reduceMotion = UmamiTheme.reduceMotion } }

        compose.runOnIdle { assertThat(reduceMotion).isTrue() }
    }
}
