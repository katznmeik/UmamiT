package com.umamit.core.data.catalog

import com.umamit.core.catalog.parser.ProductPage
import com.umamit.core.catalog.parser.ProductUrls
import com.umamit.core.network.ShopClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One product of the shop, known by its catalog [key] and its base numbers [bases] (the key and
 * the numbers merged into it, D-081), as a job that fetches its own page judges what it got: the
 * re-check of a tea missing from the sitemap (CAT-16, #97) and a tea from a shared or pasted link
 * (CAT-17, #98).
 */
internal class ShopProduct(private val client: ShopClient, val key: String, val bases: Set<String>) {
    /** [url] is a product page of this product on the shop: `/<slug>/<number>` with one of its numbers. */
    fun isNamedBy(url: HttpUrl): Boolean =
        client.isAllowed(url) && ProductUrls.articleNumberOf(url.toString())?.base in bases

    /**
     * [page] shows this product: its canonical (else the fetched URL) names it, and so does the
     * page's own number when it has one. A page of another product is never taken for this one.
     */
    fun isShownBy(page: ProductPage): Boolean {
        val canonical = page.productUrl.toHttpUrlOrNull() ?: return false
        return isNamedBy(canonical) && (page.key ?: key) in bases
    }
}
