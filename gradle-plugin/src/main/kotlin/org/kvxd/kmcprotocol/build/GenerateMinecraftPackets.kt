package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateMinecraftPackets : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataDirectory: DirectoryProperty

    @get:Input
    abstract val minecraftVersions: ListProperty<String>

    @get:Input
    abstract val runtimePackage: Property<String>

    @get:Input
    abstract val packetPackage: Property<String>

    @get:Input
    abstract val registryPackage: Property<String>

    @get:Input
    abstract val serverboundPrefix: Property<String>

    @get:Input
    abstract val clientboundPrefix: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val root = dataDirectory.get().asFile
        val specs = minecraftVersions.get().map { version -> readSpec(root, version) }

        val reports = MinecraftPacketGenerator(
            runtimePackage = runtimePackage.get(),
            packetPackage = packetPackage.get(),
            registryPackage = registryPackage.get(),
            serverboundPrefix = serverboundPrefix.get(),
            clientboundPrefix = clientboundPrefix.get(),
            outputDirectory = output.toPath(),
        ).generate(specs)

        reports.forEach { (spec, report) ->
            logger.lifecycle(
                "Generated ${report.packetCount} packets and ${report.supportTypeCount} support types for " +
                    "Minecraft ${spec.minecraftVersion} (protocol ${spec.protocolVersion})"
            )
        }
    }

    private fun readSpec(root: File, version: String): ProtocolVersionSpec {
        val protocolJson = root.protocolFile(version)
        check(protocolJson.isFile) { "Missing protocol.json for Minecraft $version at $protocolJson" }

        val versionJson = root.versionFile(version)
        check(versionJson.isFile) { "Missing version.json for Minecraft $version at $versionJson" }

        val protocolVersion = Json.parseToJsonElement(versionJson.readText())
            .jsonObject["version"]
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
            ?: throw IllegalStateException("version.json for Minecraft $version has no numeric 'version' entry.")

        return ProtocolVersionSpec(
            minecraftVersion = version,
            protocolVersion = protocolVersion,
            protocolResourcePath = "pc/$version/protocol.json",
            protocolJson = protocolJson.toPath(),
        )
    }
}
