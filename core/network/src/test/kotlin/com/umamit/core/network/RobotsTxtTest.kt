package com.umamit.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** robots.txt matching per RFC 9309 (08 §2.8, CLAUDE.md rule 7), on synthetic files. */
class RobotsTxtTest {
    // Shaped like the shop's file as described in 08 §2.2 (synthetic, not a copy).
    private val shopLike = RobotsTxt.parse(
        """
        User-agent: *
        Allow: /
        Disallow: */?
        Disallow: */widgets/
        Disallow: /account/
        Sitemap: https://www.tee-kontor-kiel.de/sitemap.xml
        """.trimIndent(),
    )

    @Test
    fun allowsImagesAndProductPagesOfTheShopLikeFile() {
        assertThat(shopLike.isAllowed("/media/ab/15/63/1727307323/photo.jpg")).isTrue()
        assertThat(shopLike.isAllowed("/bio-sencha-hisui/TG0557")).isTrue()
    }

    @Test
    fun disallowsQueriesWidgetsAndAccountPagesOfTheShopLikeFile() {
        assertThat(shopLike.isAllowed("/tee/gruener-tee/?p=2")).isFalse()
        assertThat(shopLike.isAllowed("/de/widgets/cart")).isFalse()
        assertThat(shopLike.isAllowed("/account/login")).isFalse()
    }

    @Test
    fun longestMatchWinsAndAllowWinsATie() {
        val robots = RobotsTxt.parse(
            """
            User-agent: *
            Disallow: /media/
            Allow: /media/public/
            Disallow: /tie
            Allow: /tie
            """.trimIndent(),
        )

        assertThat(robots.isAllowed("/media/secret.jpg")).isFalse()
        assertThat(robots.isAllowed("/media/public/a.jpg")).isTrue()
        assertThat(robots.isAllowed("/tie")).isTrue()
    }

    @Test
    fun dollarAnchorsTheEndOfThePath() {
        val robots = RobotsTxt.parse("User-agent: *\nDisallow: /*.jpg$\n")

        assertThat(robots.isAllowed("/media/a.jpg")).isFalse()
        assertThat(robots.isAllowed("/media/a.jpg.html")).isTrue()
    }

    @Test
    fun ownGroupIsPreferredOverTheWildcardGroupCaseInsensitively() {
        val robots = RobotsTxt.parse(
            """
            User-agent: *
            Disallow: /

            User-agent: umamit
            Disallow: /private/
            """.trimIndent(),
        )

        assertThat(robots.isAllowed("/media/a.jpg")).isTrue()
        assertThat(robots.isAllowed("/private/x")).isFalse()
    }

    @Test
    fun otherCrawlersGroupsDoNotApply() {
        val robots = RobotsTxt.parse("User-agent: SomeBot\nDisallow: /\n\nUser-agent: *\nDisallow: /media/\n")

        assertThat(robots.isAllowed("/bio-sencha-hisui/TG0557")).isTrue()
        assertThat(robots.isAllowed("/media/a.jpg")).isFalse()
    }

    @Test
    fun consecutiveUserAgentLinesShareOneGroupAndCommentsAreIgnored() {
        val robots = RobotsTxt.parse(
            """
            # synthetic
            User-agent: SomeBot
            User-agent: *   # everyone else
            Disallow: /media/ # images
            """.trimIndent(),
        )

        assertThat(robots.isAllowed("/media/a.jpg")).isFalse()
    }

    @Test
    fun emptyDisallowAndNoMatchingGroupAllowEverything() {
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow:\n").isAllowed("/media/a.jpg")).isTrue()
        assertThat(RobotsTxt.parse("User-agent: SomeBot\nDisallow: /\n").isAllowed("/media/a.jpg")).isTrue()
        assertThat(RobotsTxt.parse("").isAllowed("/media/a.jpg")).isTrue()
    }

    @Test
    fun disallowAllBlocksEverythingButRobotsTxtItself() {
        assertThat(RobotsTxt.DISALLOW_ALL.isAllowed("/media/a.jpg")).isFalse()
        assertThat(RobotsTxt.DISALLOW_ALL.isAllowed("/robots.txt")).isTrue()
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow: /\n").isAllowed("/media/a.jpg")).isFalse()
    }
}
