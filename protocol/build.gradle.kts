plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id("org.kvxd.kmcprotocol")
}

group = "org.kvxd"
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

    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(21)
}

kmcProtocol {
    minecraftVersions.set(
        providers.gradleProperty("kmcMinecraftVersions").map { it.split(',').map(String::trim) }
    )
    refreshProtocolData.set(providers.gradleProperty("kmcRefreshProtocolData").map(String::toBoolean).orElse(false))
    sourceSet.set("test")
    vendoredDataDirectory.set(layout.projectDirectory.dir("src/main/minecraft-data"))
    runtimePackage.set("org.kvxd.kmcprotocol")
    packetPackage.set("org.kvxd.kmcprotocol.packets.generated")
    registryPackage.set("org.kvxd.kmcprotocol.generated")
    serverboundPrefix.set(providers.gradleProperty("kmcPacketServerboundPrefix").orElse("Serverbound"))
    clientboundPrefix.set(providers.gradleProperty("kmcPacketClientboundPrefix").orElse("Clientbound"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<Test>("fabricServerTest") {
    group = "verification"
    description = "Runs protocol conformance tests against an automatically launched Fabric server."

    dependsOn(":fabric-testmod:classes")
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    shouldRunAfter(tasks.test)

    systemProperty("kmc.fabricServer.enabled", "true")
    systemProperty("kmc.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("kmc.protocolVersion", providers.gradleProperty("kmcFabricProtocolVersion").get())
}
