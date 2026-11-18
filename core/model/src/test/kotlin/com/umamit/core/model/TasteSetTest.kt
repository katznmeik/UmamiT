package com.umamit.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The taste sets of 08 §4a (JRN-03, TEA-02, D-128 Q12–Q16): their axes, the category rule, the override, averages. */
class TasteSetTest {

    @Test
    fun `each set has its six axes in the order of 08 4a`() {
        assertThat(TasteSet.GREEN.axes).containsExactly(
            TasteAxis.UMAMI,
            TasteAxis.SWEETNESS,
            TasteAxis.BITTERNESS,
            TasteAxis.ASTRINGENCY,
            TasteAxis.BODY,
            TasteAxis.AROMA,
        ).inOrder()
        assertThat(TasteSet.OXIDISED.axes).containsExactly(
            TasteAxis.ROAST,
            TasteAxis.SWEETNESS,
            TasteAxis.BITTERNESS,
            TasteAxis.ASTRINGENCY,
            TasteAxis.BODY,
            TasteAxis.AROMA,
        ).inOrder()
        assertThat(TasteSet.HERBAL.axes).containsExactly(
            TasteAxis.SWEETNESS,
            TasteAxis.ACIDITY,
            TasteAxis.FRESHNESS,
            TasteAxis.SPICE,
            TasteAxis.BITTERNESS,
            TasteAxis.BODY,
        ).inOrder()
        // Every axis is in some set; the stored names stay as they are (`taste_set` holds the set's name).
        assertThat(TasteSet.entries.flatMap { it.axes }.toSet()).containsExactlyElementsIn(TasteAxis.entries)
        assertThat(TasteSet.entries.map { it.name }).containsExactly("GREEN", "OXIDISED", "HERBAL").inOrder()
    }

    @Test
    fun `every category maps to its set, Sonstige and null to Gruentee`() {
        val expected = mapOf(
            TeaCategory.GREEN to TasteSet.GREEN,
            TeaCategory.WHITE to TasteSet.GREEN,
            TeaCategory.YELLOW to TasteSet.GREEN,
            TeaCategory.OOLONG to TasteSet.OXIDISED,
            TeaCategory.BLACK to TasteSet.OXIDISED,
            TeaCategory.DARK to TasteSet.OXIDISED,
            TeaCategory.HERBAL to TasteSet.HERBAL,
            TeaCategory.ROOIBOS to TasteSet.HERBAL,
            TeaCategory.FRUIT to TasteSet.HERBAL,
            TeaCategory.OTHER to TasteSet.GREEN,
        )
        assertThat(expected.keys).containsExactlyElementsIn(TeaCategory.entries)
        expected.forEach { (category, set) -> assertThat(TasteSet.of(category)).isEqualTo(set) }
        assertThat(TasteSet.of(null)).isEqualTo(TasteSet.GREEN)
    }

    @Test
    fun `the tea's Geschmacksprofil wins over its category`() {
        val herbal = Tea(id = "t", name = "Minze", category = TeaCategory.HERBAL, form = TeaForm.LOOSE_LEAF)
        assertThat(herbal.effectiveTasteSet).isEqualTo(TasteSet.HERBAL)
        assertThat(herbal.copy(tasteSet = TasteSet.GREEN).effectiveTasteSet).isEqualTo(TasteSet.GREEN)
        assertThat(herbal.copy(tasteSet = TasteSet.OXIDISED).effectiveTasteSet).isEqualTo(TasteSet.OXIDISED)
        assertThat(TasteSet.effective(null, TeaCategory.BLACK)).isEqualTo(TasteSet.OXIDISED)
        assertThat(TasteSet.effective(TasteSet.HERBAL, TeaCategory.BLACK)).isEqualTo(TasteSet.HERBAL)
    }

    @Test
    fun `a session shows its snapshot category's set until a read fills its tea's`() {
        val snapshot = SessionSnapshot(
            teaId = null, teaName = "Rooibos", teaSecondaryName = null, teaType = null, styleKey = null,
            category = TeaCategory.ROOIBOS, profileId = null, profileName = null, method = BrewMethod.KYUSU,
            vesselId = null, vesselName = null, leafGrams = null, waterMl = null, baseTempC = null,
        )
        fun session(of: SessionSnapshot) = BrewSession(
            id = "s", status = SessionStatus.COMPLETED, snapshot = of, preAlertSeconds = null,
            plannedStepCount = 1, costCents = null, startedAtMs = 0, endedAtMs = 1, zoneId = "UTC",
            autoFinished = false, steps = emptyList(),
        )
        assertThat(session(snapshot).tasteSet).isEqualTo(TasteSet.HERBAL)
        assertThat(session(snapshot.copy(category = null)).tasteSet).isEqualTo(TasteSet.GREEN)
    }

    @Test
    fun `the ten values are kept, Zuruecksetzen of a set clears its six only`() {
        val all = TasteAxis.entries.fold(TasteProfile.EMPTY) { profile, axis -> profile.with(axis, axis.ordinal % 6) }
        assertThat(TasteAxis.entries.map { all[it] }).containsExactly(0, 1, 2, 3, 4, 5, 0, 1, 2, 3).inOrder()
        assertThat(all.setCount).isEqualTo(10)
        assertThat(all.setCount(TasteSet.HERBAL)).isEqualTo(6)

        val reset = all.without(TasteSet.HERBAL.axes)
        assertThat(reset).isEqualTo(TasteProfile(umami = 0, astringency = 3, aroma = 5, roast = 0))
        assertThat(reset.setCount(TasteSet.HERBAL)).isEqualTo(0)
    }

    @Test
    fun `averages take the set's six axes only`() {
        val profiles = listOf(
            TasteProfile(umami = 3, sweetness = 2, body = 4),
            TasteProfile(sweetness = 4, acidity = 1),
        )
        // JRN-03 AC4: a herbal tea's Umami is not shown or averaged; Süße and Körper are.
        assertThat(TasteProfile.averages(profiles, TasteSet.HERBAL)).containsExactly(
            TasteAxis.SWEETNESS,
            3.0,
            TasteAxis.ACIDITY,
            1.0,
            TasteAxis.BODY,
            4.0,
        )
        assertThat(TasteProfile.averages(profiles)).containsExactly(
            TasteAxis.UMAMI,
            3.0,
            TasteAxis.SWEETNESS,
            3.0,
            TasteAxis.BODY,
            4.0,
        )
        assertThat(TasteProfile.averages(profiles, TasteSet.OXIDISED)).doesNotContainKey(TasteAxis.UMAMI)
    }
}
