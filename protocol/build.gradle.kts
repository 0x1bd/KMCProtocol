plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "de.kvxd"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.network)
    implementation(libs.ktor.network.tls)

    implementation(libs.adventure.nbt)
    implementation(libs.adventure.text.serializer.gson)
    // implementation(libs.cloudburstnbt)

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.coroutines)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}