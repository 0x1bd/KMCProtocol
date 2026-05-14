package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that generates Kotlin packet classes from a PrismarineJS protocol.json file.
 */
@CacheableTask
abstract class GenerateMinecraftPackets : DefaultTask() {

    /**
     * protocol.json file used as input.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protocolDataFile: RegularFileProperty

    /**
     * Minecraft data version represented by the input protocol file.
     */
    @get:Input
    abstract val minecraftVersion: Property<String>

    /**
     * Runtime package containing KMCProtocol core classes.
     */
    @get:Input
    abstract val runtimePackage: Property<String>

    /**
     * Package where generated packet classes are written.
     */
    @get:Input
    abstract val packetPackage: Property<String>

    /**
     * Package where generated registry helpers are written.
     */
    @get:Input
    abstract val registryPackage: Property<String>

    /**
     * Prefix applied to generated serverbound packet classes.
     */
    @get:Input
    abstract val serverboundPrefix: Property<String>

    /**
     * Prefix applied to generated clientbound packet classes.
     */
    @get:Input
    abstract val clientboundPrefix: Property<String>

    /**
     * Directory where generated Kotlin sources are written.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Generates packet sources and registry helpers.
     */
    @TaskAction
    fun generate() {
        val protocol = Json.parseToJsonElement(protocolDataFile.get().asFile.readText()).jsonObject
        val output = outputDirectory.get().asFile
        project.delete(output)
        output.mkdirs()

        val options = PacketGenerationOptions(
            minecraftVersion = minecraftVersion.get(),
            runtimePackage = runtimePackage.get(),
            packetPackage = packetPackage.get(),
            registryPackage = registryPackage.get(),
            serverboundPrefix = serverboundPrefix.get(),
            clientboundPrefix = clientboundPrefix.get(),
            outputDirectory = output.toPath(),
        )

        val report = MinecraftPacketGenerator(protocol, options).generate()
        logger.lifecycle(
            "Generated ${report.packetCount} packet classes and ${report.supportTypeCount} support types " +
                "from ${protocolDataFile.get().asFile}"
        )
    }
}
