pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // google() first and limited to Google's groups, so Maven Central is only
        // asked for artifacts it hosts (fewer requests, fewer HTTP 429 answers).
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "UmamiT"

include(":app")

// Core modules (06 §3). Pure Kotlin/JVM: model, common, domain, timer-engine, catalog-parser.
include(
    ":core:model",
    ":core:common",
    ":core:domain",
    ":core:timer-engine",
    ":core:timer",
    ":core:catalog-parser",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":core:content",
    ":core:data",
    ":core:backup",
    ":core:designsystem",
    ":core:ui",
    ":core:testing",
)

// Feature modules (06 §3). Features depend only on core:* (enforced in build-logic).
include(
    ":feature:onboarding",
    ":feature:home",
    ":feature:brew",
    ":feature:collection",
    ":feature:catalog",
    ":feature:journal",
    ":feature:stats",
    ":feature:teaware",
    ":feature:settings",
)
