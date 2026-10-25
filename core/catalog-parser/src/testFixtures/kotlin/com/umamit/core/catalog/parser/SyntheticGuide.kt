package com.umamit.core.catalog.parser

/**
 * Synthetic *Tee-Info* guide pages for tests (D-016): the structure of the shop's blog articles
 * (08 §2.7) with invented titles, texts and images. Nothing here is taken from the shop.
 */
object SyntheticGuide {
    /** A gallery image's path on the shop (`/media/…` with the `?ts=` query). */
    fun imagePath(name: String) = "/media/gg/hh/ii/1700000000/$name.jpg?ts=1700000000"

    /**
     * A guide page as the shop builds it: canonical link, JSON-LD `BlogPosting` ([jsonLd], null
     * for none), breadcrumb, a teaser of another article, and the blog detail block
     * ([bodyBlock]) with the title `h1`, an optional [gallery] (each image repeated as the slider
     * does) and the article [body] HTML.
     */
    @Suppress("LongParameterList")
    fun page(
        baseUrl: String = "https://shop.example",
        slug: String = "erfundener-gruentee-guide",
        title: String = "Ein erfundener Grüntee-Guide",
        jsonLd: String? = blogPosting(baseUrl, slug, title, image = "$baseUrl${imagePath("hero")}"),
        gallery: List<String> = emptyList(),
        body: String = "<p>Ein erfundener Absatz.</p>",
        bodyBlock: Boolean = true,
    ): String = buildString {
        append("<!DOCTYPE html><html lang=\"de-DE\"><head><meta charset=\"utf-8\">")
        append("<title>Synthetic shop</title>")
        append("<link rel=\"canonical\" href=\"$baseUrl/tee-info/$slug\">")
        if (jsonLd != null) append("<script type=\"application/ld+json\">$jsonLd</script>")
        append("<script src=\"/cdn-cgi/challenge-platform/scripts/beacon.js\"></script>")
        append("</head><body><main class=\"content-main\"><div class=\"container-main pixel-blog-container\">")
        append("<nav><ol class=\"breadcrumb\"><li class=\"breadcrumb-item\"><a href=\"/blog\">Blog</a></li></ol></nav>")
        append("<div class=\"cms-page is-pixelblog\"><div class=\"cms-section\">")
        // A teaser of another article next to the article: never part of it.
        append("<div class=\"cms-element-text pixel-blog-teaser\"><p>Ein anderer Artikel.</p></div>")
        if (bodyBlock) {
            append("<div class=\"cms-block cms-block-text pixel-blog-detail pos-1\"><div class=\"row\">")
            append("<div class=\"sitebar-blog-right col-lg-12\"><div class=\"cms-element-text\">")
            append("<div class=\"title\"><h1>${title.htmlEscaped()}</h1></div>")
            append("<div class=\"description\">")
            if (gallery.isNotEmpty()) append(galleryHtml(baseUrl, gallery))
            append(body)
            append("</div></div></div></div></div>")
        }
        append("</div></div></div></main></body></html>")
    }

    /** A JSON-LD `BlogPosting` as the shop writes it. */
    fun blogPosting(
        baseUrl: String,
        slug: String,
        title: String,
        image: String?,
        published: String = "2024-03-12T09:30:00+01:00",
    ): String = buildString {
        append("{\"@context\":\"https://schema.org\",\"@type\":\"BlogPosting\",")
        append("\"mainEntityOfPage\":{\"@type\":\"WebPage\",\"@id\":\"$baseUrl/tee-info/$slug\"},")
        append("\"url\":\"$baseUrl/tee-info/$slug\",\"headline\":\"${title.jsonEscaped()}\",")
        append("\"name\":\"${title.jsonEscaped()}\",\"description\":\"Eine erfundene Beschreibung.\",")
        append("\"articleBody\":\"Ein erfundener Text.\",\"wordCount\":4,")
        append("\"datePublished\":\"$published\",\"dateModified\":\"$published\",\"inLanguage\":\"de-DE\",")
        if (image != null) append("\"image\":{\"@type\":\"ImageObject\",\"url\":\"$image\"},")
        append("\"author\":{\"@type\":\"Organization\",\"name\":\"Beispiel\",\"url\":\"$baseUrl\"},")
        append("\"publisher\":{\"@type\":\"Organization\",\"name\":\"Beispiel\",\"url\":\"$baseUrl\",")
        append("\"logo\":{\"@type\":\"ImageObject\",\"url\":\"$baseUrl/media/logo.png\"}},")
        append("\"isAccessibleForFree\":true}")
    }

    /** The slider: each image as a slide and as a thumbnail, both with a lazyload placeholder `src`. */
    private fun galleryHtml(baseUrl: String, images: List<String>): String = buildString {
        append("<div class=\"st-blog-gallery\"><div class=\"gallery-slider\">")
        images.forEach { append(lazyImage(baseUrl, it, "gallery-slider-item")) }
        append("</div><div class=\"gallery-slider-thumbnails\">")
        images.forEach { append(lazyImage(baseUrl, it, "gallery-slider-thumbnails-image")) }
        append("</div><button class=\"base-slider-controls-prev\">‹</button></div>")
    }

    private fun lazyImage(baseUrl: String, path: String, kind: String) =
        "<img class=\"$kind lazyload\" src=\"data:image/gif;base64,R0lGODlhAQABAAAAACw=\" " +
            "data-src=\"$baseUrl$path\" data-full-image=\"$baseUrl$path\" alt=\"\">"

    private fun String.htmlEscaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"")
}
