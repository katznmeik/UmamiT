package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.HarvestSeason
import org.junit.Test

/**
 * The conversion of catalog data into collection-tea fields (08 §2.3 second table, CAT-12) on
 * invented texts (D-016).
 */
class CollectionFieldsTest {
    @Test
    fun theHarvestYearIsTheFirstPlausibleYearOfBadgeAndTitle() {
        assertThat(CollectionFields.harvestYear(2026, "Mai 2025", "2026 Shincha Morgennebel")).isEqualTo(2025)
        assertThat(CollectionFields.harvestYear(2026, "Frühling", "2026 Shincha Morgennebel")).isEqualTo(2026)
        assertThat(CollectionFields.harvestYear(2026, null, "Morgennebel")).isNull()
        // Next year's harvest is announced at the end of a year; anything beyond, or before 2000, is no harvest year.
        assertThat(CollectionFields.harvestYear(2026, "2027", null)).isEqualTo(2027)
        assertThat(CollectionFields.harvestYear(2026, "2028", "1999")).isNull()
        assertThat(CollectionFields.harvestYear(2026, "Artikel 12026", null)).isNull()
        assertThat(CollectionFields.harvestYear(2026, null, null)).isNull()
    }

    /**
     * Live sample 2 (D-089): the prose names travel reports' years („Reisebericht 2015") and a
     * garden's founding („gründete 2009"); the harvest year is only the tea's own badge and title.
     */
    @Test
    fun theProsesYearsAreNeverTheHarvestYear() {
        assertThat(CollectionFields.harvestYear(2026, badge = null, title = "Bergdorf Gyokuro")).isNull()
        assertThat(CollectionFields.harvestYear(2026, badge = "Frühling", title = "Bergdorf Sencha")).isNull()
    }

    @Test
    fun theHarvestSeasonComesFromTheBadgeElseTheProsesFlushWords() {
        listOf(
            Triple("first flush", null, HarvestSeason.FIRST_FLUSH),
            Triple("Frühlingsernte", null, HarvestSeason.FIRST_FLUSH),
            Triple("Erste Pflückung im April", null, HarvestSeason.FIRST_FLUSH),
            Triple("Ichibancha", null, HarvestSeason.FIRST_FLUSH),
            Triple("Shincha", null, HarvestSeason.FIRST_FLUSH),
            Triple("Second Flush", null, HarvestSeason.SECOND_FLUSH),
            Triple("Nibancha", null, HarvestSeason.SECOND_FLUSH),
            Triple("Sommerernte, Anfang Juli", null, HarvestSeason.SUMMER),
            Triple("Sommer", null, HarvestSeason.SUMMER),
            Triple("Herbst", null, HarvestSeason.AUTUMN),
            // A badge the table doesn't name is an unknown season.
            Triple("Handgepflückt", null, HarvestSeason.UNKNOWN),
            // Without a badge, the prose's flush words.
            Triple(null, "Eine Frühlingsernte aus dem Süden.", HarvestSeason.FIRST_FLUSH),
            Triple(null, "Aus der Nibancha-Pflückung.", HarvestSeason.SECOND_FLUSH),
            // In the prose a bare season is no harvest: „im Sommer kalt aufgießen".
            Triple(null, "Im Sommer auch kalt aufgießen.", null),
            Triple(null, null, null),
        ).forEach { (badge, prose, expected) ->
            assertWithMessage("$badge / $prose").that(CollectionFields.harvestSeason(badge, prose)).isEqualTo(expected)
        }
    }

    @Test
    fun shadingDaysAreDaysOrWeeksWithRangesAtTheirMidpoint() {
        assertThat(CollectionFields.shadingDays("6 Tage", null)).isEqualTo(6)
        assertThat(CollectionFields.shadingDays("2 Wochen", null)).isEqualTo(14)
        assertThat(CollectionFields.shadingDays("14-21 Tage", null)).isEqualTo(18)
        assertThat(CollectionFields.shadingDays("3 bis 4 Wochen", null)).isEqualTo(25)
        // A badge without a number is empty; the prose then counts only where it speaks of shading.
        assertThat(CollectionFields.shadingDays("ja", null)).isNull()
        assertThat(CollectionFields.shadingDays(null, "Die Pflanzen wurden für 6 Tage beschattet.")).isEqualTo(6)
        assertThat(CollectionFields.shadingDays(null, "Hält sich 3 Tage im Kühlschrank.")).isNull()
        // Never beyond the field's 60 days (05 §3.1).
        assertThat(CollectionFields.shadingDays("12 Wochen", null)).isNull()
        assertThat(CollectionFields.shadingDays(null, null)).isNull()
    }

    @Test
    fun theOriginCountryBecomesItsIsoCode() {
        assertThat(CollectionFields.countryCode("Japan")).isEqualTo("JP")
        assertThat(CollectionFields.countryCode("China")).isEqualTo("CN")
        assertThat(CollectionFields.countryCode("Taiwan")).isEqualTo("TW")
        assertThat(CollectionFields.countryCode("Südkorea")).isEqualTo("KR")
        assertThat(CollectionFields.countryCode("Korea")).isEqualTo("KR")
        // Several countries: the first one known.
        assertThat(CollectionFields.countryCode("Atlantis, japan")).isEqualTo("JP")
        assertThat(CollectionFields.countryCode("Atlantis")).isNull()
        assertThat(CollectionFields.countryCode(null)).isNull()
    }
}
