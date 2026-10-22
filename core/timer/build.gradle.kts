plugins {
    alias(libs.plugins.umamit.android.library)
    alias(libs.plugins.umamit.hilt)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Robolectric tests for alarm scheduling, notification content and the
    // service (04 §12).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test>().configureEach {
    // Robolectric downloads its Android framework jars at test time. Use the
    // Maven Central host the cloud container reaches (CLAUDE.md, Environment);
    // `repo1.maven.org` answers HTTP 429 there.
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    // Robolectric's SDK 36 sandbox touches FileDescriptor internals (JDK 17+).
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}
