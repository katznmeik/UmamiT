package com.umamit.core.catalog.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** The sitemap reader (08 §2.4 item 1, D-076): an index, a url set, plain or gzip-compressed. */
class SitemapReaderTest {
    @Test
    fun anIndexListsItsSitemaps() {
        val xml = SyntheticShop.sitemapIndex("$SHOP/sitemap/one.xml.gz", "$SHOP/sitemap/two.xml.gz")

        assertThat(SitemapReader.read(xml.toByteArray()))
            .isEqualTo(Sitemap.Index(listOf("$SHOP/sitemap/one.xml.gz", "$SHOP/sitemap/two.xml.gz")))
    }

    @Test
    fun aUrlSetListsItsUrlsWithEntitiesDecoded() {
        val xml = SyntheticShop.urlSet(listOf("$SHOP/a/XY0100", "$SHOP/b/XY0200?x=1&y=2"))

        assertThat(SitemapReader.read(xml.toByteArray()))
            .isEqualTo(Sitemap.UrlSet(listOf("$SHOP/a/XY0100", "$SHOP/b/XY0200?x=1&y=2")))
    }

    @Test
    fun aGzipSitemapIsDecompressedByTheApp() {
        val gz = SyntheticShop.gzip(SyntheticShop.urlSet(listOf("$SHOP/a/XY0100")))

        assertThat(SitemapReader.read(gz)).isEqualTo(Sitemap.UrlSet(listOf("$SHOP/a/XY0100")))
    }

    @Test
    fun anEmptyUrlSetIsStillAUrlSet() {
        assertThat(SitemapReader.read(SyntheticShop.urlSet(emptyList()).toByteArray()))
            .isEqualTo(Sitemap.UrlSet(emptyList()))
    }

    @Test
    fun otherDocumentsAreInvalid() {
        listOf(
            "",
            "<html><body>Wartung</body></html>",
            "not xml at all",
            """<?xml version="1.0"?><feed><entry><loc>x</loc></entry></feed>""",
        ).forEach {
            assertThat(SitemapReader.read(it.toByteArray())).isInstanceOf(Sitemap.Invalid::class.java)
        }
    }

    @Test
    fun aBrokenGzipIsInvalid() {
        val gz = SyntheticShop.gzip(SyntheticShop.urlSet(listOf("$SHOP/a/XY0100")))

        assertThat(SitemapReader.read(gz.copyOf(gz.size / 2))).isInstanceOf(Sitemap.Invalid::class.java)
    }

    @Test
    fun aGzipThatInflatesBeyondTheSitemapLimitIsInvalid() {
        val bomb = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { gz ->
                val zeros = ByteArray(1024 * 1024)
                repeat(SitemapReader.MAX_XML_BYTES / zeros.size + 1) { gz.write(zeros) }
            }
        }.toByteArray()

        assertThat(SitemapReader.read(bomb)).isInstanceOf(Sitemap.Invalid::class.java)
    }

    private companion object {
        const val SHOP = "https://shop.example"
    }
}
