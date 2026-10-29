package com.umamit.core.catalog.parser

/**
 * The shop's Kräutertee section (v0.2.1, D-093; 08 §2.2, §2.4 item 3), read from a live
 * breadcrumb on 2026-10-29: Tee › Kräutertee › <sub-section>. Its sub-sections are herbal blends,
 * single herbs, organic herbs, Ayurveda and mate/lapacho, all in scope; fruit tea
 * (`/tee/fruechtetee/`) and rooibos (`/tee/rotbusch/`) are sections of their own and stay out.
 */
internal object HerbalSection {
    private const val PATH = "/tee/kraeutertee/"

    /** [path] lies in the Kräutertee section. */
    fun contains(path: String): Boolean = normalised(path).startsWith(PATH)

    /**
     * A Kräutertee's *Sorte*: the title of the deepest breadcrumb item below the section
     * (`/tee/kraeutertee/<sub-section>/`); null for one filed in the section itself.
     */
    fun sorte(breadcrumb: List<Crumb>): String? =
        breadcrumb.lastOrNull { contains(it.path) && normalised(it.path) != PATH }?.title

    private fun normalised(path: String) = path.lowercase().let { if (it.endsWith("/")) it else "$it/" }
}
