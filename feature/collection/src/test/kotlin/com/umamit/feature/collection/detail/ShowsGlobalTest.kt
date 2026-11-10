package com.umamit.feature.collection.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The *Knapp ab* field draws SET-16's value in the quieter colour without making it the text (D-110). */
class ShowsGlobalTest {
    private val quieter = Color(0xFF6B6B6B)

    @Test
    fun `shows the global value in the given colour and maps every offset to 0`() {
        val result = ShowsGlobal("15", quieter).filter(AnnotatedString(""))

        assertThat(result.text.text).isEqualTo("15")
        assertThat(result.text.spanStyles.map { it.item.color }).containsExactly(quieter)
        assertThat(result.text.spanStyles.single().start).isEqualTo(0)
        assertThat(result.text.spanStyles.single().end).isEqualTo(2)
        assertThat(result.offsetMapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(result.offsetMapping.transformedToOriginal(0)).isEqualTo(0)
        assertThat(result.offsetMapping.transformedToOriginal(2)).isEqualTo(0)
    }
}
