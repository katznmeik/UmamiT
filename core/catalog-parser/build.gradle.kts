plugins {
    alias(libs.plugins.umamit.jvm.library)
    // Synthetic shop pages and sitemaps (D-016) shared with the refresh tests in core:data.
    `java-test-fixtures`
}

dependencies {
    // HTML and XML parsing of the shop's pages and sitemaps (08 §2.3, §2.4); pure Java.
    implementation(libs.jsoup)
}
