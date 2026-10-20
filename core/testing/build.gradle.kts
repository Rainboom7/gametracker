import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared test helpers. Add as `testImplementation(projects.core.testing)`.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
}
