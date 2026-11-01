package com.umamit.core.domain.brew

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import org.junit.Test

/**
 * Ratio scaling (BRW-03, 05 §7): leaf to 0,1 g, water to 5 ml, half up. The
 * expected values are the seed recipes of 08 §2.5a and their brewing grid.
 */
class BrewScalingTest {

    @Test
    fun `the Kyusu grid of both seed teas, 4 g per 200 ml, gives 5,0 g in the 250 ml Kyusu`() {
        assertThat(BrewScaling.leafFor(Ratio(leafGrams = 4.0, waterMl = 200), waterMl = 250)).isEqualTo(5.0)
    }

    @Test
    fun `the western grid, 10 g per litre, gives 4,0 g in the 400 ml Becher`() {
        assertThat(BrewScaling.leafFor(Ratio(leafGrams = 10.0, waterMl = 1000), waterMl = 400)).isEqualTo(4.0)
    }

    @Test
    fun `the Kyusu recipe in the 400 ml Becher takes 8,0 g, and back in the Kyusu 5,0 g`() {
        val kyusu = Ratio(leafGrams = 5.0, waterMl = 250)

        assertThat(BrewScaling.leafFor(kyusu, waterMl = 400)).isEqualTo(8.0)
        assertThat(BrewScaling.leafFor(kyusu, waterMl = 250)).isEqualTo(5.0)
    }

    @Test
    fun `the Westlich recipe in the 250 ml Kyusu takes 2,5 g`() {
        assertThat(BrewScaling.leafFor(Ratio(leafGrams = 4.0, waterMl = 400), waterMl = 250)).isEqualTo(2.5)
    }

    @Test
    fun `leaf rounds half up to 0,1 g`() {
        // 4 g / 400 ml × 245 ml = 2,45 g → 2,5 g (half up, not the binary 2,4499…).
        assertThat(BrewScaling.leafFor(Ratio(leafGrams = 4.0, waterMl = 400), waterMl = 245)).isEqualTo(2.5)
        // 5 g / 250 ml × 13 ml = 0,26 g → 0,3 g.
        assertThat(BrewScaling.leafFor(Ratio(leafGrams = 5.0, waterMl = 250), waterMl = 13)).isEqualTo(0.3)
    }

    @Test
    fun `changing the leaf rescales the water to 5 ml, half up`() {
        val kyusu = Ratio(leafGrams = 5.0, waterMl = 250)

        assertThat(BrewScaling.waterFor(kyusu, leafGrams = 8.0)).isEqualTo(400)
        assertThat(BrewScaling.waterFor(kyusu, leafGrams = 5.1)).isEqualTo(255)
        // 5,05 g → 252,5 ml → 255 ml (half up); 5,04 g → 252 ml → 250 ml.
        assertThat(BrewScaling.waterFor(kyusu, leafGrams = 5.05)).isEqualTo(255)
        assertThat(BrewScaling.waterFor(kyusu, leafGrams = 5.04)).isEqualTo(250)
    }

    @Test
    fun `scaled values stay within the recipe bounds of 05 §3_2`() {
        val strong = Ratio(leafGrams = 10.0, waterMl = 100)
        val weak = Ratio(leafGrams = 0.1, waterMl = 3000)

        // Leaf 0,1–100 g.
        assertThat(BrewScaling.leafFor(strong, waterMl = 3000)).isEqualTo(100.0)
        assertThat(BrewScaling.leafFor(weak, waterMl = 10)).isEqualTo(0.1)
        // Water 10–3000 ml.
        assertThat(BrewScaling.waterFor(weak, leafGrams = 100.0)).isEqualTo(3000)
        assertThat(BrewScaling.waterFor(strong, leafGrams = 0.1)).isEqualTo(10)
    }

    @Test
    fun `the steppers move the leaf by 0,1 g and the water by 5 ml within the bounds`() {
        assertThat(BrewScaling.stepLeaf(4.0, up = true)).isEqualTo(4.1)
        assertThat(BrewScaling.stepLeaf(4.0, up = false)).isEqualTo(3.9)
        assertThat(BrewScaling.stepLeaf(0.1, up = false)).isEqualTo(0.1)
        assertThat(BrewScaling.stepLeaf(100.0, up = true)).isEqualTo(100.0)
        assertThat(BrewScaling.stepWater(250, up = true)).isEqualTo(255)
        assertThat(BrewScaling.stepWater(252, up = false)).isEqualTo(250)
        assertThat(BrewScaling.stepWater(10, up = false)).isEqualTo(10)
        assertThat(BrewScaling.stepWater(3000, up = true)).isEqualTo(3000)
    }

    @Test
    fun `a vessel brews its usable volume, else its capacity (WARE-04)`() {
        assertThat(Vessel("k", "Kyusu", VesselType.KYUSU, capacityMl = 300, usableMl = 250).brewMl()).isEqualTo(250)
        assertThat(Vessel("b", "Becher", VesselType.MUG_INFUSER, capacityMl = 400).brewMl()).isEqualTo(400)
    }

    @Test
    fun `the ratio per 100 ml is rounded half up to 0,1 g (PRF-03 AC1)`() {
        assertThat(BrewScaling.per100Ml(leafGrams = 4.0, waterMl = 250)).isEqualTo(1.6)
        assertThat(BrewScaling.per100Ml(leafGrams = 5.0, waterMl = 200)).isEqualTo(2.5)
        assertThat(BrewScaling.per100Ml(leafGrams = 1.0, waterMl = 300)).isEqualTo(0.3)
    }
}
