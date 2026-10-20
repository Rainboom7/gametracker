// Plugins go on the root buildscript classpath (instead of `plugins { alias(...) apply false }`)
// so the Android Gradle Plugin is only resolved when Android modules are part of the build.
// That keeps `-Pplaylog.jvmOnly=true` working on machines without Google Maven / Android SDK.
buildscript {
    val jvmOnly = providers.gradleProperty("playlog.jvmOnly").orNull?.toBoolean() ?: false
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
    dependencies {
        classpath(libs.kotlin.gradlePlugin)
        classpath(libs.kotlin.serializationPlugin)
        if (!jvmOnly) {
            classpath(libs.android.gradlePlugin)
            classpath(libs.kotlin.composeCompilerPlugin)
            classpath(libs.ksp.gradlePlugin)
            classpath(libs.hilt.gradlePlugin)
        }
    }
}
