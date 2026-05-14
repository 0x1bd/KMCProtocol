package org.kvxd.kmcprotocol.build

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import javax.inject.Inject

/**
 * Gradle extension for configuring packet generation and protocol resources.
 */
abstract class KmcProtocolExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Minecraft data version used for packet generation.
     */
    abstract val minecraftVersion: Property<String>

    /**
     * Whether protocol.json should be refreshed from upstream.
     */
    abstract val refreshProtocolData: Property<Boolean>

    /**
     * Runtime package containing KMCProtocol core classes.
     */
    abstract val runtimePackage: Property<String>

    /**
     * Package where generated packet classes are written.
     */
    abstract val packetPackage: Property<String>

    /**
     * Package where generated registry helpers are written.
     */
    abstract val registryPackage: Property<String>

    /**
     * Prefix applied to generated serverbound packet classes.
     */
    abstract val serverboundPrefix: Property<String>

    /**
     * Prefix applied to generated clientbound packet classes.
     */
    abstract val clientboundPrefix: Property<String>

    /**
     * Source set that receives generated packet sources and protocol resources.
     */
    abstract val sourceSet: Property<String>

    /**
     * protocol.json file used as the packet-generation source.
     */
    abstract val protocolDataFile: RegularFileProperty

    /**
     * Directory where generated Kotlin packet sources are written.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Configuration for the local Minecraft test server tasks.
     */
    val testServer: KmcProtocolTestServerExtension = objects.newInstance(KmcProtocolTestServerExtension::class.java)

    /**
     * Configures local Minecraft test server tasks.
     */
    fun testServer(configure: KmcProtocolTestServerExtension.() -> Unit) {
        testServer.configure()
    }
}

/**
 * Gradle DSL for local Minecraft test server task settings.
 */
abstract class KmcProtocolTestServerExtension {
    /**
     * URL used to download the Minecraft server jar.
     */
    abstract val serverJarUrl: Property<String>

    /**
     * Directory where the local server is prepared and run.
     */
    abstract val serverDir: DirectoryProperty

    /**
     * Host or address assigned to server.properties.
     */
    abstract val host: Property<String>

    /**
     * Port assigned to server.properties.
     */
    abstract val port: Property<Int>

    /**
     * Java executable used by runMinecraftServer.
     */
    abstract val javaExecutable: Property<String>

    /**
     * JVM arguments used by runMinecraftServer.
     */
    abstract val javaArgs: ListProperty<String>

    /**
     * Whether prepareMinecraftServer should download the server jar even when one exists.
     */
    abstract val forceDownload: Property<Boolean>
}

/**
 * Gradle task that downloads and prepares an offline local Minecraft server.
 */
abstract class PrepareMinecraftServer : DefaultTask() {
    /**
     * URL used to download the Minecraft server jar.
     */
    @get:Input
    abstract val serverJarUrl: Property<String>

    /**
     * Host or address assigned to server.properties.
     */
    @get:Input
    abstract val serverHost: Property<String>

    /**
     * Port assigned to server.properties.
     */
    @get:Input
    abstract val serverPort: Property<Int>

    /**
     * Whether the server jar should be downloaded even when one already exists.
     */
    @get:Input
    abstract val forceDownload: Property<Boolean>

    /**
     * Directory where server files are prepared.
     */
    @get:OutputDirectory
    abstract val serverDir: DirectoryProperty

    /**
     * Prepares the server jar, EULA file, and server.properties.
     */
    @TaskAction
    fun prepare() {
        val dir = serverDir.get().asFile
        check(dir.mkdirs() || dir.isDirectory) {
            "Could not create Minecraft server directory: ${dir.absolutePath}"
        }

        val serverJar = dir.resolve("server.jar")
        if (!serverJar.isFile || forceDownload.get()) {
            logger.lifecycle("Downloading Minecraft server jar to ${serverJar.absolutePath}")
            java.net.URI(serverJarUrl.get()).toURL().openStream().use { input ->
                serverJar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            logger.lifecycle("Using existing Minecraft server jar at ${serverJar.absolutePath}")
        }

        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText(
            """
            #Minecraft server properties
            #Generated by org.kvxd.kmcprotocol Gradle plugin
            online-mode=false
            server-ip=${serverHost.get()}
            server-port=${serverPort.get()}
            level-name=world
            gamemode=creative
            difficulty=easy
            max-players=20
            enable-query=false
            enable-rcon=false
            motd=KMCProtocol test server
            """.trimIndent() + "\n"
        )
    }
}

/**
 * Gradle plugin that wires Minecraft packet generation into Kotlin and Java source sets.
 */
class KmcProtocolPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<KmcProtocolExtension>("kmcProtocol")

        extension.minecraftVersion.convention(project.providers.gradleProperty("kmcMinecraftVersion"))
        extension.refreshProtocolData.convention(false)
        extension.runtimePackage.convention("org.kvxd.kmcprotocol")
        extension.packetPackage.convention("org.kvxd.kmcprotocol.packets.generated")
        extension.registryPackage.convention("org.kvxd.kmcprotocol.generated")
        extension.serverboundPrefix.convention("Serverbound")
        extension.clientboundPrefix.convention("Clientbound")
        extension.sourceSet.convention("main")
        extension.protocolDataFile.convention(
            extension.minecraftVersion.map { version ->
                project.layout.buildDirectory.file("kmcProtocol/minecraft-data/pc/$version/protocol.json").get()
            }
        )
        extension.outputDirectory.convention(project.layout.buildDirectory.dir("generated/sources/kmcPackets/kotlin"))
        val protocolResourceDirectory = project.layout.buildDirectory.dir("generated/resources/kmcProtocol")

        extension.testServer.serverJarUrl.convention(project.providers.gradleProperty("kmcMinecraftServerJarUrl"))
        extension.testServer.serverDir.convention(project.layout.buildDirectory.dir("kmc/server"))
        extension.testServer.host.convention("127.0.0.1")
        extension.testServer.port.convention(25565)
        extension.testServer.javaExecutable.convention("${System.getProperty("java.home")}/bin/java")
        extension.testServer.javaArgs.convention(listOf("-Xmx1024M", "-Xms1024M"))
        extension.testServer.forceDownload.convention(false)

        val download = project.tasks.register("downloadMinecraftProtocolData") {
            inputs.property("minecraftVersion", extension.minecraftVersion)
            inputs.property("refreshProtocolData", extension.refreshProtocolData)
            outputs.file(extension.protocolDataFile)

            doLast {
                val output = extension.protocolDataFile.get().asFile
                if (output.exists() && !extension.refreshProtocolData.get()) {
                    project.logger.lifecycle("Using cached minecraft-data protocol source: ${output.relativeTo(project.projectDir)}")
                    return@doLast
                }

                output.parentFile.mkdirs()
                val sourceUrl =
                    "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/${extension.minecraftVersion.get()}/protocol.json"

                project.logger.lifecycle("Downloading minecraft-data protocol source from $sourceUrl")
                sourceUrl.toUrl().openStream().use { input ->
                    output.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            }
        }

        val generate = project.tasks.register<GenerateMinecraftPackets>("generateMinecraftPackets") {
            dependsOn(download)

            protocolDataFile.set(extension.protocolDataFile)
            outputDirectory.set(extension.outputDirectory)
            minecraftVersion.set(extension.minecraftVersion)
            runtimePackage.set(extension.runtimePackage)
            packetPackage.set(extension.packetPackage)
            registryPackage.set(extension.registryPackage)
            serverboundPrefix.set(extension.serverboundPrefix)
            clientboundPrefix.set(extension.clientboundPrefix)
        }

        val prepareProtocolResources = project.tasks.register<Copy>("prepareMinecraftProtocolResources") {
            group = "build"
            description = "Copies generated Minecraft protocol data into build resources."
            dependsOn(download)

            from(extension.protocolDataFile)
            into(extension.minecraftVersion.map { version ->
                protocolResourceDirectory.get().dir("pc/$version")
            })
        }

        project.afterEvaluate {
            val sourceSetName = extension.sourceSet.get()
            project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                project.extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named(sourceSetName) {
                    kotlin.srcDir(extension.outputDirectory)
                }
                val compileTask = if (sourceSetName == "main") {
                    "compileKotlin"
                } else {
                    "compile${sourceSetName.replaceFirstChar { it.uppercase() }}Kotlin"
                }
                project.tasks.named(compileTask) {
                    dependsOn(generate)
                }
            }

            project.pluginManager.withPlugin("java") {
                project.extensions.getByType<SourceSetContainer>().named(sourceSetName) {
                    resources.srcDir(protocolResourceDirectory)
                }
                val processResourcesTask = if (sourceSetName == "main") {
                    "processResources"
                } else {
                    "process${sourceSetName.replaceFirstChar { it.uppercase() }}Resources"
                }
                project.tasks.named(processResourcesTask) {
                    dependsOn(prepareProtocolResources)
                }
            }
        }

        val prepareServer = project.tasks.register<PrepareMinecraftServer>("prepareMinecraftServer") {
            group = "verification"
            description = "Downloads and configures an offline Minecraft server for local protocol testing."

            serverDir.set(extension.testServer.serverDir)
            serverJarUrl.set(extension.testServer.serverJarUrl)
            serverHost.set(extension.testServer.host)
            serverPort.set(extension.testServer.port)
            forceDownload.set(extension.testServer.forceDownload)
        }

        project.tasks.register<Exec>("runMinecraftServer") {
            group = "verification"
            description = "Prepares and runs the offline Minecraft server used for local protocol testing."
            dependsOn(prepareServer)

            doFirst {
                val dir = prepareServer.get().serverDir.get().asFile
                workingDir = dir
                commandLine(
                    listOf(extension.testServer.javaExecutable.get()) +
                        extension.testServer.javaArgs.get() +
                        listOf("-jar", dir.resolve("server.jar").absolutePath, "nogui")
                )
            }
        }
    }

    private fun String.toUrl(): java.net.URL = java.net.URI(this).toURL()
}
