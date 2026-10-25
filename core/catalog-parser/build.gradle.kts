plugins {
    alias(libs.plugins.umamit.jvm.library)
    // Synthetic shop pages and sitemaps (D-016) shared with the refresh tests in core:data.
    `java-test-fixtures`
}

dependencies {
    // The built-in styles and the recipe types the derivation produces (CAT-04, CAT-06).
    api(project(":core:domain"))
    implementation(project(":core:common")) // snap5 (GEN-12)
    // HTML and XML parsing of the shop's pages and sitemaps (08 §2.3, §2.4); pure Java.
    implementation(libs.jsoup)
    // The guides' JSON-LD BlogPosting: title, date and image (08 §2.7, CAT-09).
    implementation(libs.kotlinx.serialization.json)
    // Derived recipes must be valid plans for the timer engine (#68).
    testImplementation(project(":core:timer-engine"))
}
