import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
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
    api(projects.platform.api)
    implementation(projects.core.network)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
