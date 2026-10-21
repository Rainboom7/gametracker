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
        classpath(libs.detekt.gradlePlugin)
        classpath(libs.kover.gradlePlugin)
        if (!jvmOnly) {
            classpath(libs.android.gradlePlugin)
            classpath(libs.kotlin.composeCompilerPlugin)
            classpath(libs.ksp.gradlePlugin)
            classpath(libs.hilt.gradlePlugin)
        }
    }
}

// Quality gates: detekt on every module, kover coverage merged at the root,
// and `*LiveTest` classes (real network, need secrets) split out of `test` into `liveTest`.
apply(plugin = "org.jetbrains.kotlinx.kover")

subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
        // Findings that existed when detekt was introduced; CI fails only on new ones.
        // Regenerate with `./gradlew detektBaseline` after fixing old findings.
        baseline.set(rootProject.file("config/detekt/baseline/${path.removePrefix(":").replace(':', '-')}.xml"))
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        rootProject.dependencies.add("kover", this@subprojects)
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            currentProject.instrumentation.disabledForTestTasks.add("liveTest")
        }

        val testTask = tasks.named<Test>("test")
        testTask.configure { exclude("**/*LiveTest*") }
        tasks.register<Test>("liveTest") {
            description = "Runs *LiveTest against the real services; tests without secrets are skipped."
            group = "verification"
            testClassesDirs = testTask.get().testClassesDirs
            classpath = testTask.get().classpath
            include("**/*LiveTest*")
            outputs.upToDateWhen { false }
        }
    }
    tasks.withType<Test>().configureEach {
        if (name.endsWith("UnitTest")) exclude("**/*LiveTest*")
    }

    // Hilt 2.58 / Room ship kotlin-metadata-jvm 2.2.x, which reads metadata only up to 2.3.0, but
    // kotlinx-serialization 1.12 is built with Kotlin 2.4 (metadata 2.4.0). Use the reader that
    // matches our compiler on every annotation-processor classpath; it reads one version ahead.
    configurations.matching { it.name.startsWith("ksp") || it.name.startsWith("kapt") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
                useVersion(libs.versions.kotlin.get())
                because("read Kotlin 2.4 metadata from kotlinx-serialization 1.12")
            }
        }
    }

    // Experimental Compose APIs are fine for a personal app: opt in module-wide instead of per call
    // site, so a newly used experimental API does not break the build. Unknown markers only warn.
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
            compilerOptions.optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "androidx.compose.foundation.ExperimentalFoundationApi",
                "androidx.compose.foundation.layout.ExperimentalLayoutApi",
                "androidx.compose.animation.ExperimentalAnimationApi",
                "androidx.compose.animation.ExperimentalSharedTransitionApi",
                "androidx.compose.ui.ExperimentalComposeUiApi",
                "androidx.compose.ui.text.ExperimentalTextApi",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
