package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Article numbers and product URLs from the sitemap, grouped per base number (08 §2.2, §2.4 items 1–2). */
class ProductUrlsTest {
    @Test
    fun articleNumbersKeepTheBaseAndNameTheVariant() {
        val cases = mapOf(
            "XY0100" to Triple("XY0100", null, false),
            "XY0100-50" to Triple("XY0100", "50", false),
            "XY0100-1000" to Triple("XY0100", "1000", false),
            "XY0100M" to Triple("XY0100", null, false),
            "XY0100a" to Triple("XY0100a", null, false),
            "XY0100a-50" to Triple("XY0100a", "50", false),
            "XYZ01234-25" to Triple("XYZ01234", "25", false),
            "XY0100-250mittel" to Triple("XY0100", "250mittel", false),
            "XY0100-M" to Triple("XY0100", "M", false),
            "XY0100SET" to Triple("XY0100SET", null, true),
            "XY0100SET-2" to Triple("XY0100SET", "2", true),
        )
        cases.forEach { (text, expected) ->
            val number = ArticleNumber.parse(text)
            assertThat(number).isNotNull()
            assertThat(Triple(number!!.base, number.variant, number.isSet)).isEqualTo(expected)
        }
    }

    @Test
    fun aGramsVariantCarriesItsGrams() {
        assertThat(ArticleNumber.parse("XY0100-250")!!.grams).isEqualTo(250)
        assertThat(ArticleNumber.parse("XY0100-250mittel")!!.grams).isNull()
        assertThat(ArticleNumber.parse("XY0100")!!.grams).isNull()
    }

    @Test
    fun otherTokensAreNoArticleNumbers() {
        listOf("", "tee-info", "a-guide-to-tea", "XY", "0100", "xy0100", "XY0100-", "XY0100 50").forEach {
            assertThat(ArticleNumber.parse(it)).isNull()
        }
    }

    @Test
    fun productUrlsAreGroupedPerBaseNumberWithTheParentPage() {
        val products = ProductUrls.group(
            listOf(
                "$SHOP/zweiter-tee/XY0200-50",
                "$SHOP/morgennebel-sencha/XY0100",
                "$SHOP/morgennebel-sencha/XY0100-10",
                "$SHOP/morgennebel-sencha/XY0100-250",
                "$SHOP/morgennebel-sencha/XY0100M",
                "$SHOP/zweiter-tee/XY0200-10",
                "$SHOP/zweiter-tee/XY0200M",
            ),
        )

        assertThat(products.groups.map { it.base }).containsExactly("XY0100", "XY0200").inOrder()
        val first = products.groups[0]
        assertThat(first.pageUrl).isEqualTo("$SHOP/morgennebel-sencha/XY0100")
        assertThat(first.variantUrls).containsExactly(
            "XY0100-10",
            "$SHOP/morgennebel-sencha/XY0100-10",
            "XY0100-250",
            "$SHOP/morgennebel-sencha/XY0100-250",
        )
        // Without a parent URL the M page (the parent's page under another number) is fetched.
        assertThat(products.groups[1].pageUrl).isEqualTo("$SHOP/zweiter-tee/XY0200M")
    }

    @Test
    fun withoutParentOrMPageTheSmallestVariantIsFetched() {
        val products = ProductUrls.group(listOf("$SHOP/t/XY0300-250", "$SHOP/t/XY0300-50", "$SHOP/t/XY0300-500"))

        assertThat(products.groups.single().pageUrl).isEqualTo("$SHOP/t/XY0300-50")
    }

    @Test
    fun lowerCaseSuffixesAreSeparateArticles() {
        val products = ProductUrls.group(listOf("$SHOP/a/XY0400", "$SHOP/b/XY0400a", "$SHOP/b/XY0400a-50"))

        assertThat(products.groups.map { it.base }).containsExactly("XY0400", "XY0400a").inOrder()
    }

    @Test
    fun setsAreSkippedWithoutARequest() {
        val products = ProductUrls.group(listOf("$SHOP/set/XY0500SET", "$SHOP/a/XY0100"))

        assertThat(products.groups.map { it.base }).containsExactly("XY0100")
        assertThat(products.sets).isEqualTo(1)
    }

    @Test
    fun setsAreRecognisedInAnyCaseAndWithALetterAfterTheirMark() {
        // Full live run 1 (D-091): „…Set" got through the upper-case-only rule; the shop also
        // writes „…SETc" and „…SETSALE", and „-SET" as a variant.
        listOf("XY0100Set", "XY0100set", "XY0100SET", "XY0100SETc", "XY0100SetSALE", "XY0100-SET", "XY0100-Set")
            .forEach { assertWithMessage(it).that(ArticleNumber.parse(it)?.isSet).isTrue() }
        listOf("XY0100", "XY0100a", "XY0100SALE", "XY0100-250").forEach {
            assertWithMessage(it).that(ArticleNumber.parse(it)?.isSet).isFalse()
        }
    }

    @Test
    fun saleNumbersAreReducedDuplicates() {
        listOf("XY0100SALE", "XY0100Sale", "XY0100SETSALE").forEach {
            assertWithMessage(it).that(ArticleNumber.parse(it)?.isSale).isTrue()
        }
        listOf("XY0100", "XY0100a", "XY0100SET", "XY0100-50").forEach {
            assertWithMessage(it).that(ArticleNumber.parse(it)?.isSale).isFalse()
        }
    }

    @Test
    fun setsAndSaleDuplicatesAreSkippedWithoutARequest() {
        val products = ProductUrls.group(
            listOf(
                "$SHOP/a/XY0100",
                "$SHOP/a/XY0100SALE",
                "$SHOP/s/XY0500Set",
                "$SHOP/s/XY0600SETc",
                "$SHOP/s/XY0700SETSALE",
                "$SHOP/b/XY0800SALE",
            ),
        )

        assertThat(products.groups.map { it.base }).containsExactly("XY0100")
        assertThat(products.sets).isEqualTo(3)
        assertThat(products.sales).isEqualTo(2)
    }

    @Test
    fun guidesCategoriesAndOtherPagesAreNoProducts() {
        val products = ProductUrls.group(
            listOf(
                "$SHOP/",
                "$SHOP/tee-info/ein-erfundener-ratgeber",
                "$SHOP/tee/gruener-tee/",
                "$SHOP/tee/gruener-tee/japan/sencha/",
                "$SHOP/impressum",
                "$SHOP/a/b/XY0100",
                "$SHOP/a/XY0100/",
                "not a url",
            ),
        )

        assertThat(products.groups).isEmpty()
    }

    private companion object {
        const val SHOP = "https://shop.example"
    }
}
