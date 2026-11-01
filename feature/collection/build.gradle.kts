plugins {
    alias(libs.plugins.umamit.android.feature)
}

dependencies {
    // *Meine Tees* (SCR-08): the rows, the settings (collectionSort) and *Wiederherstellen*. The tea detail
    // (SCR-09): teas, recipes, vessels, stock, the journal, the catalog's reorder link (INV-09) and TeaArchive.
    implementation(project(":core:data"))
    // Search, filters and sort as pure functions (CollectionBrowser; TEA-09–TEA-11).
    implementation(project(":core:domain"))
    // Rezept bearbeiten (SCR-12): each step's Vorwarnung shows the lead the timer will give it (TMR-23), and
    // *Aufguss hinzufügen* appends the timer's *Weiterer Aufguss* (TMR-10), as the Schritte-Sheet does.
    implementation(project(":core:timer-engine"))
    implementation(libs.kotlinx.coroutines.core)
}
