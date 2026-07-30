plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    application
    id("org.kvxd.kmcprotocol")
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

kmcProtocol {
    minecraftVersions.set(
        providers.gradleProperty("kmcMinecraftVersions").map { it.split(',').map(String::trim) }
    )
    refreshProtocolData.set(providers.gradleProperty("kmcRefreshProtocolData").map(String::toBoolean).orElse(false))
    addRuntimeDependency.set(false)
    vendoredDataDirectory.set(project(":protocol").layout.projectDirectory.dir("src/main/minecraft-data"))
    runtimePackage.set("org.kvxd.kmcprotocol")
    packetPackage.set("org.kvxd.kmcprotocol.packets.generated")
    registryPackage.set("org.kvxd.kmcprotocol.generated")
    serverboundPrefix.set(providers.gradleProperty("kmcPacketServerboundPrefix").orElse("Serverbound"))
    clientboundPrefix.set(providers.gradleProperty("kmcPacketClientboundPrefix").orElse("Clientbound"))
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
