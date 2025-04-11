plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

group = "de.kvxd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation(libs.bundles.ktor)

    implementation(libs.bundles.serialization)

    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.kotlin.coroutines)

    implementation(libs.keventbus)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}