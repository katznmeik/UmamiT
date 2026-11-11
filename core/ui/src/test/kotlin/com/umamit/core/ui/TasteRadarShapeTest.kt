package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TasteProfile
import org.junit.Test

/** The pure parts of `TasteRadar` and `TasteSlider` (03 §3.8, JRN-03): when it is drawn, where points lie. */
class TasteRadarShapeTest {
    @Test
    fun `the radar is drawn from three set axes on`() {
        assertThat(TasteRadarShape.isDrawn(emptyMap())).isFalse()
        assertThat(TasteRadarShape.isDrawn(mapOf(TasteAxis.UMAMI to 4.0, TasteAxis.BODY to 2.0))).isFalse()
        assertThat(
            TasteRadarShape.isDrawn(mapOf(TasteAxis.UMAMI to 4.0, TasteAxis.BODY to 2.0, TasteAxis.AROMA to 0.0)),
        ).isTrue()
    }

    @Test
    fun `an axis at zero counts as set, an absent one does not`() {
        val profile = TasteProfile(umami = 0, sweetness = 0, aroma = 0)

        assertThat(profile.radarValues().keys).containsExactly(TasteAxis.UMAMI, TasteAxis.SWEETNESS, TasteAxis.AROMA)
        assertThat(TasteRadarShape.isDrawn(profile.radarValues())).isTrue()
    }

    @Test
    fun `an unset axis lies at the centre and a full one on the rim, the first axis straight up`() {
        val centre = TasteRadarShape.point(TasteAxis.SWEETNESS, value = null, radius = 100f)
        val top = TasteRadarShape.point(TasteAxis.UMAMI, value = 5.0, radius = 100f)
        val half = TasteRadarShape.point(TasteAxis.UMAMI, value = 2.5, radius = 100f)

        assertThat(centre.first).isWithin(EPS).of(0f)
        assertThat(centre.second).isWithin(EPS).of(0f)
        assertThat(top.first).isWithin(EPS).of(0f)
        assertThat(top.second).isWithin(EPS).of(-100f)
        assertThat(half.second).isWithin(EPS).of(-50f)
    }

    @Test
    fun `the axes follow clockwise at sixty degrees`() {
        val second = TasteRadarShape.point(TasteAxis.SWEETNESS, value = 5.0, radius = 100f)
        val fourth = TasteRadarShape.point(TasteAxis.ASTRINGENCY, value = 5.0, radius = 100f)

        assertThat(second.first).isGreaterThan(80f)
        assertThat(second.second).isWithin(EPS).of(-50f)
        assertThat(fourth.first).isWithin(EPS).of(0f)
        assertThat(fourth.second).isWithin(EPS).of(100f)
    }

    @Test
    fun `values are read as whole numbers or one German decimal`() {
        assertThat(TasteRadarShape.valueText(4.0)).isEqualTo("4")
        assertThat(TasteRadarShape.valueText(3.5)).isEqualTo("3,5")
        assertThat(TasteRadarShape.valueText(2.333)).isEqualTo("2,3")
        assertThat(TasteRadarShape.valueText(2.96)).isEqualTo("3")
    }

    private companion object {
        const val EPS = 0.01f
    }
}
