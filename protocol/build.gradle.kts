import org.kvxd.kmcprotocol.build.GenerateMinecraftPackets

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

group = "org.kvxd"
version = "1.0-SNAPSHOT"

val minecraftDataVersion = providers.gradleProperty("kmcMinecraftVersion").orElse("1.21.4")
val refreshMinecraftProtocolData = providers.gradleProperty("kmcRefreshProtocolData").orElse("false")
val minecraftProtocolDataFile = layout.projectDirectory.file(
    "src/main/minecraft-data/pc/${minecraftDataVersion.get()}/protocol.json"
)
val generatedPacketSources = layout.buildDirectory.dir("generated/sources/kmcPackets/kotlin")

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

    sourceSets {
        main {
            kotlin.srcDir(generatedPacketSources)
            resources.srcDir("src/main/minecraft-data")
        }
    }
}

val downloadMinecraftProtocolData by tasks.registering {
    inputs.property("minecraftDataVersion", minecraftDataVersion)
    inputs.property("refreshMinecraftProtocolData", refreshMinecraftProtocolData)
    outputs.file(minecraftProtocolDataFile)

    doLast {
        val output = minecraftProtocolDataFile.asFile
        val shouldRefresh = refreshMinecraftProtocolData.get().toBooleanStrictOrNull() ?: false

        if (output.exists() && !shouldRefresh) {
            logger.lifecycle("Using cached minecraft-data protocol source: ${output.relativeTo(projectDir)}")
            return@doLast
        }

        output.parentFile.mkdirs()

        val sourceUrl =
            "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/${minecraftDataVersion.get()}/protocol.json"

        logger.lifecycle("Downloading minecraft-data protocol source from $sourceUrl")
        uri(sourceUrl).toURL().openStream().use { input ->
            output.outputStream().use { outputStream ->
                input.copyTo(outputStream)
            }
        }
    }
}

val generateMinecraftPackets by tasks.registering(GenerateMinecraftPackets::class) {
    dependsOn(downloadMinecraftProtocolData)

    protocolDataFile.set(minecraftProtocolDataFile)
    outputDirectory.set(generatedPacketSources)
    minecraftVersion.set(minecraftDataVersion)
    basePackage.set("org.kvxd.kmcprotocol")
}

tasks.named("compileKotlin") {
    dependsOn(generateMinecraftPackets)
}

tasks.named("processResources") {
    dependsOn(downloadMinecraftProtocolData)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    providers.gradleProperty("kmcRealServerTest").orNull?.let {
        systemProperty("kmc.realServer.enabled", it)
    }
    providers.gradleProperty("kmcRealServerHost").orNull?.let {
        systemProperty("kmc.realServer.host", it)
    }
    providers.gradleProperty("kmcRealServerPort").orNull?.let {
        systemProperty("kmc.realServer.port", it)
    }
    providers.gradleProperty("kmcRealServerJar").orNull?.let {
        systemProperty("kmc.realServer.jar", it)
    }
    providers.gradleProperty("kmcRealServerProtocolVersion").orNull?.let {
        systemProperty("kmc.realServer.protocolVersion", it)
    }
    providers.gradleProperty("kmcFabricServerTest").orNull?.let {
        systemProperty("kmc.fabricServer.enabled", it)
    }
    providers.gradleProperty("kmcFabricServerHost").orNull?.let {
        systemProperty("kmc.fabricServer.host", it)
    }
    providers.gradleProperty("kmcFabricServerPort").orNull?.let {
        systemProperty("kmc.fabricServer.port", it)
    }
}

val realServerTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs packet status-flow tests against a real Paper/Fabric-compatible server."

    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    shouldRunAfter(tasks.test)

    systemProperty("kmc.realServer.enabled", "true")
}

val fabricServerTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs generated packet ACK tests against the Fabric packet test mod."

    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    shouldRunAfter(tasks.test)

    systemProperty("kmc.fabricServer.enabled", "true")
}
