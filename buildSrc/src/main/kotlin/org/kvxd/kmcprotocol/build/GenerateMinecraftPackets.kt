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

@CacheableTask
abstract class GenerateMinecraftPackets : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protocolDataFile: RegularFileProperty

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val serverboundPrefix: Property<String>

    @get:Input
    abstract val clientboundPrefix: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val protocol = Json.parseToJsonElement(protocolDataFile.get().asFile.readText()).jsonObject
        val output = outputDirectory.get().asFile
        project.delete(output)
        output.mkdirs()

        val options = PacketGenerationOptions(
            minecraftVersion = minecraftVersion.get(),
            basePackage = basePackage.get(),
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
