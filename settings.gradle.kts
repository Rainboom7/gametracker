pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.(android|testing).*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.(android|testing).*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Playlog"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Pure Kotlin/JVM modules: domain, platform clients, sync. Build and test without the Android SDK.
include(
    ":core:model",
    ":core:common",
    ":core:network",
    ":core:data",
    ":platform:api",
    ":platform:steam",
    ":platform:playstation",
    ":platform:nintendo",
)

// Android modules. `-Pplaylog.jvmOnly=true` skips them on machines without the SDK / Google Maven.
val jvmOnly = providers.gradleProperty("playlog.jvmOnly").orNull?.toBoolean() ?: false
if (!jvmOnly) {
    include(
        ":app",
        ":core:database",
        ":core:designsystem",
        ":core:ui",
        ":feature:library",
        ":feature:game",
        ":feature:activity",
        ":feature:screenshots",
        ":feature:profile",
    )
}
