plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

group = "de.kvxd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.ktor)

    implementation(libs.bundles.serialization)

    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.kotlin.coroutines)

    implementation(libs.ksignal)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}