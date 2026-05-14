plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "org.kvxd"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":protocol"))
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.kotlin.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.knbt)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.kvxd.kmcprotocol.example.StatusPingExampleKt")
}

tasks.register<JavaExec>("runPlayStateChatSmokeServer") {
    group = "application"
    description = "Runs an offline-mode 1.21.4 play-state chat smoke server."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.kvxd.kmcprotocol.example.PlayStateChatSmokeServerKt")
}

tasks.test {
    useJUnitPlatform()
}
