plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
