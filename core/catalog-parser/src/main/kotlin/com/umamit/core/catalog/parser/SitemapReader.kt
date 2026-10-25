package com.umamit.core.catalog.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/** One sitemap document (sitemaps.org protocol 0.9). */
sealed interface Sitemap {
    /** A sitemap index: the URLs of further sitemaps. */
    data class Index(val sitemaps: List<String>) : Sitemap

    /** A url set: the listed page URLs. */
    data class UrlSet(val urls: List<String>) : Sitemap

    /** Not a sitemap (or broken); [reason] is technical English for the diagnostics. */
    data class Invalid(val reason: String) : Sitemap
}

/**
 * Reads the shop's sitemaps (08 §2.4 item 1): `/sitemap.xml` is an index of `.xml.gz` url sets.
 * The shop sends the `.xml.gz` without `Content-Encoding`, so a gzip body (magic `1f 8b`) is
 * decompressed here (D-076), up to [MAX_XML_BYTES].
 */
object SitemapReader {
    /**
     * The largest uncompressed sitemap read: far above the shop's ~0.7 MB (08 §2.2), below the
     * protocol's 50 MB, so a broken or hostile gzip can't exhaust the phone's memory.
     */
    const val MAX_XML_BYTES = 16 * 1024 * 1024

    private const val GZIP_MAGIC_1 = 0x1f.toByte()
    private const val GZIP_MAGIC_2 = 0x8b.toByte()
    private const val BUFFER_BYTES = 64 * 1024
    private const val INDEX = "sitemapindex"
    private const val URL_SET = "urlset"

    /** [body] (plain or gzip XML) as a [Sitemap]; never throws. */
    fun read(body: ByteArray): Sitemap = try {
        parse(if (body.isGzip()) gunzip(body) else body)
    } catch (e: IOException) {
        Sitemap.Invalid("gzip: ${e.message}")
    }

    private fun parse(xml: ByteArray): Sitemap {
        val document = Jsoup.parse(xml.decodeToString(), "", Parser.xmlParser())
        val root = document.children().firstOrNull { it.localName() == INDEX || it.localName() == URL_SET }
            ?: return Sitemap.Invalid("no $INDEX or $URL_SET")
        val isIndex = root.localName() == INDEX
        val locs = root.children()
            .filter { it.localName() == if (isIndex) "sitemap" else "url" }
            .mapNotNull { entry -> entry.children().firstOrNull { it.localName() == "loc" } }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        return if (isIndex) Sitemap.Index(locs) else Sitemap.UrlSet(locs)
    }

    /** The element's name without a namespace prefix. */
    private fun Element.localName() = normalName().substringAfter(':')

    private fun ByteArray.isGzip() = size >= 2 && this[0] == GZIP_MAGIC_1 && this[1] == GZIP_MAGIC_2

    /** The inflated bytes; more than [MAX_XML_BYTES] fail like a broken gzip. */
    private fun gunzip(body: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(body)).use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var read = input.read(buffer)
        while (read >= 0) {
            out.write(buffer, 0, read)
            if (out.size() > MAX_XML_BYTES) throw IOException("over $MAX_XML_BYTES bytes uncompressed")
            read = input.read(buffer)
        }
        out.toByteArray()
    }
}
