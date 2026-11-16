package com.umamit.core.data.catalog

import com.umamit.core.network.ShopClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** [url] as a URL on the client's host allow-list, or null: nothing elsewhere is ever fetched (rule 7). */
internal fun ShopClient.allowedUrl(url: String): HttpUrl? = url.toHttpUrlOrNull()?.takeIf { isAllowed(it) }
