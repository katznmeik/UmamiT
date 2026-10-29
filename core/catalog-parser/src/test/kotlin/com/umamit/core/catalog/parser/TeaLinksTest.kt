package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * A tea link the user shares or pastes (CAT-17 AC2, 08 §2.4 item 9, D-094): the shared text is
 * untrusted, so only `https://www.tee-kontor-kiel.de/<slug>/<article number>` with an optional
 * `-<grams>` is accepted; anything else is refused before any request. Invented slugs and numbers
 * (D-016).
 */
class TeaLinksTest {
    @Test
    fun aProductLinkIsAccepted() {
        val link = TeaLinks.find("https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170")

        assertThat(link).isEqualTo(TeaLinkResult.Accepted(TeaLink("/nebelhang-sencha/XY0170", "XY0170")))
    }

    @Test
    fun theLinkIsAlwaysReadOnTheShopsHostWithHttps() {
        val link = accepted("HTTPS://WWW.Tee-Kontor-Kiel.de/nebelhang-sencha/XY0170")

        assertThat(link.url).isEqualTo("https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170")
    }

    @Test
    fun aSizeSuffixIsAcceptedAndNamesTheSameTea() {
        assertThat(
            accepted("$SHOP/nebelhang-sencha/XY0170-50"),
        ).isEqualTo(TeaLink("/nebelhang-sencha/XY0170-50", "XY0170"))
        assertThat(accepted("$SHOP/nebelhang-sencha/XY0170-1000").base).isEqualTo("XY0170")
    }

    @Test
    fun theShopsOtherNumberShapesAreAccepted() {
        // The parent's second number, a separate article with a lower-case letter, a longer prefix (08 §2.2).
        assertThat(accepted("$SHOP/nebelhang-sencha/XY0170M").base).isEqualTo("XY0170")
        assertThat(accepted("$SHOP/abendtau-sencha/XY0220a").base).isEqualTo("XY0220a")
        assertThat(accepted("$SHOP/bio-wiesenzauber-kraeutertee/XYZ01234-250").base).isEqualTo("XYZ01234")
    }

    @Test
    fun textAroundTheLinkInAShareIsIgnored() {
        val shares = listOf(
            "Nebelhang Sencha | Tee-Kontor Kiel $SHOP/nebelhang-sencha/XY0170",
            "Nebelhang Sencha\n$SHOP/nebelhang-sencha/XY0170\n",
            "  $SHOP/nebelhang-sencha/XY0170\t",
            // The same link twice is still one link.
            "$SHOP/nebelhang-sencha/XY0170 – $SHOP/nebelhang-sencha/XY0170",
        )
        shares.forEach { text ->
            assertWithMessage(text).that(TeaLinks.find(text))
                .isEqualTo(TeaLinkResult.Accepted(TeaLink("/nebelhang-sencha/XY0170", "XY0170")))
        }
    }

    @Test
    fun otherHostsAreRefused() {
        refused(
            "https://www.example.com/nebelhang-sencha/XY0170",
            "https://tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://shop.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.com/nebelhang-sencha/XY0170",
        )
    }

    @Test
    fun lookAlikeHostsAreRefused() {
        refused(
            // A suffix, a prefix, a trailing dot, a homoglyph (Cyrillic е), punycode, a typo.
            "https://www.tee-kontor-kiel.de.example.com/nebelhang-sencha/XY0170",
            "https://evil-www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de./nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiеl.de/nebelhang-sencha/XY0170",
            "https://xn--www.tee-kontor-kil-xyz.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kie1.de/nebelhang-sencha/XY0170",
            "https://www.example.com/www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de\\@example.com/nebelhang-sencha/XY0170",
            // Letters that only match the host or the scheme when case is ignored: the Kelvin sign,
            // a dotless ı, a dotted İ, a long ſ in the scheme.
            "https://www.tee-\u212Aontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-k\u0131el.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-k\u0130el.de/nebelhang-sencha/XY0170",
            "http\u017F://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            // Any non-ASCII character anywhere in the link.
            "https://www.tee-kontor-kiel.de/nebelhang-sench\u00E4/XY0170",
        )
    }

    @Test
    fun otherSchemesAreRefused() {
        refused(
            "http://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "ftp://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "intent://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https:/www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https:\\\\www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "//www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
        )
    }

    @Test
    fun credentialsAndPortsAreRefused() {
        refused(
            "https://user@www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://user:secret@www.tee-kontor-kiel.de/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de@example.com/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de:443/nebelhang-sencha/XY0170",
            "https://www.tee-kontor-kiel.de:8443/nebelhang-sencha/XY0170",
        )
    }

    @Test
    fun aQueryStringIsRefused() {
        refused(
            "$SHOP/nebelhang-sencha/XY0170?p=2",
            "$SHOP/nebelhang-sencha/XY0170?",
            "$SHOP/nebelhang-sencha/XY0170?utm_source=share",
        )
    }

    @Test
    fun aFragmentIsRefused() {
        // Never sent to the shop, but nothing the ticket allows: refused in doubt (CLAUDE.md rule 7).
        refused("$SHOP/nebelhang-sencha/XY0170#bewertungen", "$SHOP/nebelhang-sencha/XY0170#")
    }

    @Test
    fun categoryAndGuideUrlsAreRefused() {
        refused(
            "$SHOP/",
            "$SHOP",
            "$SHOP/tee/gruener-tee/japan/sencha/",
            "$SHOP/tee/gruener-tee/",
            "$SHOP/tee/kraeutertee",
            "$SHOP/tee-info/ein-guide-fuer-japantee",
            "$SHOP/tee-info/XY0170",
            "$SHOP/nebelhang-sencha/XY0170/",
            "$SHOP/media/aa/XY0170.jpg",
            "$SHOP/sitemap.xml",
            "$SHOP/robots.txt",
        )
    }

    @Test
    fun otherPathShapesAreRefused() {
        refused(
            "$SHOP/nebelhang-sencha/xy0170",
            "$SHOP/nebelhang-sencha/XY0170-",
            "$SHOP/nebelhang-sencha/XY0170-250mittel",
            "$SHOP/nebelhang-sencha/XY0170-0",
            "$SHOP/nebelhang-sencha/XY0170-M",
            "$SHOP//XY0170",
            "$SHOP/nebelhang%2Dsencha/XY0170",
            "$SHOP/nebelhang-sencha/XY%300170",
            "$SHOP/../nebelhang-sencha/XY0170",
            "$SHOP/nebelhang-sencha/../XY0170",
            "$SHOP/nebelhang.sencha/XY0170",
            "$SHOP/nebelhang-sençha/XY0170",
            "$SHOP/nebelhang-sencha/XY0170;jsessionid=1",
        )
    }

    @Test
    fun setsAndSaleDuplicatesAreNoCatalogTeasAndAreNotFetched() {
        listOf(
            "$SHOP/sencha-probierset/XY0170SET",
            "$SHOP/sencha-probierset/XY0170Set-2",
            "$SHOP/nebelhang-sencha-reduziert/XY0170SALE",
        ).forEach { link ->
            assertWithMessage(link).that(TeaLinks.find(link)).isEqualTo(TeaLinkResult.OutOfScope)
        }
    }

    @Test
    fun noLinkOrSeveralLinksAreRefused() {
        refused(
            "",
            "   ",
            "Nebelhang Sencha",
            "nebelhang-sencha/XY0170",
            // Two different links: which one was meant is in doubt.
            "$SHOP/nebelhang-sencha/XY0170 $SHOP/morgennebel-sencha/XY0100",
            "$SHOP/nebelhang-sencha/XY0170 https://www.example.com/",
        )
    }

    @Test
    fun aLinkGluedToOtherTextIsRefused() {
        refused(
            "Tee:$SHOP/nebelhang-sencha/XY0170",
            "<$SHOP/nebelhang-sencha/XY0170>",
            "($SHOP/nebelhang-sencha/XY0170)",
            "\"$SHOP/nebelhang-sencha/XY0170\"",
            // A no-break space is no separator: the token isn't a link of its own.
            "Tee $SHOP/nebelhang-sencha/XY0170",
            "$SHOP/nebelhang-sencha/XY0170​",
        )
    }

    @Test
    fun overlongTextOrLinksAreRefusedUnread() {
        val slug = "a".repeat(TeaLinks.MAX_LINK_LENGTH)
        refused("$SHOP/$slug/XY0170")
        val padding = " ".repeat(TeaLinks.MAX_TEXT_LENGTH)
        refused("$SHOP/nebelhang-sencha/XY0170$padding")
        // Just within the limits, the link is read.
        val title = "x".repeat(TeaLinks.MAX_TEXT_LENGTH - 60)
        assertThat(
            TeaLinks.find("$title $SHOP/nebelhang-sencha/XY0170"),
        ).isInstanceOf(TeaLinkResult.Accepted::class.java)
    }

    private fun accepted(text: String): TeaLink {
        val result = TeaLinks.find(text)
        assertWithMessage(text).that(result).isInstanceOf(TeaLinkResult.Accepted::class.java)
        return (result as TeaLinkResult.Accepted).link
    }

    private fun refused(vararg texts: String) = texts.forEach { text ->
        assertWithMessage(text).that(TeaLinks.find(text)).isEqualTo(TeaLinkResult.Refused)
    }

    private companion object {
        const val SHOP = "https://www.tee-kontor-kiel.de"
    }
}
