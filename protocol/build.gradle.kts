plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    id("org.kvxd.kmcprotocol")
}

group = "org.kvxd"
version = "1.0-SNAPSHOT"

val minecraftDataVersion = providers.gradleProperty("kmcMinecraftVersion")

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
    minecraftVersion.set(minecraftDataVersion)
    refreshProtocolData.set(providers.gradleProperty("kmcRefreshProtocolData").map(String::toBoolean).orElse(false))
    sourceSet.set("test")
    protocolDataFile.set(layout.projectDirectory.file("src/main/minecraft-data/pc/${minecraftDataVersion.get()}/protocol.json"))
    runtimePackage.set("org.kvxd.kmcprotocol")
    packetPackage.set("org.kvxd.kmcprotocol.packets.generated")
    registryPackage.set("org.kvxd.kmcprotocol.generated")
    serverboundPrefix.set(providers.gradleProperty("kmcPacketServerboundPrefix").orElse("Serverbound"))
    clientboundPrefix.set(providers.gradleProperty("kmcPacketClientboundPrefix").orElse("Clientbound"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val fabricServerTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs protocol conformance tests against an automatically launched Fabric server."

    dependsOn(":fabric-testmod:classes")
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    shouldRunAfter(tasks.test)

    systemProperty("kmc.fabricServer.enabled", "true")
    systemProperty("kmc.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("kmc.protocolVersion", providers.gradleProperty("kmcProtocolVersion").get())
}
