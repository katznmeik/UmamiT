package com.umamit.core.ui

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.DescriptorGroup
import com.umamit.core.model.TasteDescriptor
import org.junit.Test

/** Every descriptor and group has its own string resource (CLAUDE.md rule 4). */
class DescriptorLabelsTest {

    @Test
    fun `every descriptor has its own label resource`() {
        val ids = TasteDescriptor.entries.map { it.labelRes() }
        assertThat(ids.toSet()).hasSize(TasteDescriptor.entries.size)
    }

    @Test
    fun `every group has its own label resource`() {
        val ids = DescriptorGroup.entries.map { it.labelRes() }
        assertThat(ids.toSet()).hasSize(DescriptorGroup.entries.size)
    }
}
