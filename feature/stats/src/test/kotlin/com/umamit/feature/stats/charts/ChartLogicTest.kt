package com.umamit.feature.stats.charts

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure parts of the charts: the y axis, label thinning, percentages and the donut's colours. */
class ChartLogicTest {

    @Test
    fun `axis labels are round and cover the tallest bar`() {
        assertThat(niceAxis(6.0).labels).containsExactly(0.0, 5.0, 10.0).inOrder()
        assertThat(niceAxis(1.0).labels).containsExactly(0.0, 0.5, 1.0).inOrder()
        assertThat(niceAxis(37.0).max).isEqualTo(40.0)
        assertThat(niceAxis(64.8).labels).containsExactly(0.0, 50.0, 100.0).inOrder()
        assertThat(niceAxis(50.0).labels).containsExactly(0.0, 25.0, 50.0).inOrder()
        assertThat(niceAxis(0.0).labels).containsExactly(0.0, 1.0, 2.0).inOrder()
    }

    @Test
    fun `x labels thin out to the stride that fits and always keep the last one`() {
        assertThat(labelStride(slot = 30f, labelWidth = 20f, gap = 5f)).isEqualTo(1)
        val stride = labelStride(slot = 12f, labelWidth = 40f, gap = 8f)
        assertThat(stride).isEqualTo(4)
        val shown = (0 until 30).filter { showsLabel(it, 30, stride) }
        assertThat(shown).contains(29)
        assertThat(shown.zipWithNext { a, b -> b - a }.toSet()).containsExactly(stride)
    }

    @Test
    fun `slices keep their own colour unless taken and never share one`() {
        val green = Color(0xFF00AA00)
        val blue = Color(0xFF0000AA)
        val other = Color(0xFF888888)
        val palette = listOf(green, blue, Color(0xFFAA0000), Color(0xFFAAAA00))
        val slices = listOf(
            DonutSlice("A", 5, 0, green),
            DonutSlice("B", 4, 0, green),
            DonutSlice("C", 3, 0, null),
            DonutSlice("D", 2, 0, blue),
            DonutSlice("Andere", 1, 0, isOther = true),
        )
        val colors = assignSliceColors(slices, palette, other)
        assertThat(colors[0]).isEqualTo(green)
        assertThat(colors[3]).isEqualTo(blue)
        assertThat(colors[4]).isEqualTo(other)
        assertThat(colors.take(4).toSet()).hasSize(4)
    }

    @Test
    fun `two slices with the same label keep two keys and two colours`() {
        val slices = listOf(
            DonutSlice("Sencha", 5, 0, key = "tea:1"),
            DonutSlice("Sencha", 4, 0, key = "tea:2"),
        )
        val palette = listOf(Color(0xFF00AA00), Color(0xFF0000AA))

        assertThat(slices.map { it.key }.toSet()).hasSize(2)
        assertThat(DonutSlice("Gyokuro", 1, 0).key).isEqualTo("Gyokuro")
        assertThat(assignSliceColors(slices, palette, Color.Gray).toSet()).hasSize(2)
    }
}
