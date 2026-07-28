package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path

internal class MinecraftPacketGenerator(
    private val runtimePackage: String,
    private val packetPackage: String,
    private val registryPackage: String,
    private val serverboundPrefix: String,
    private val clientboundPrefix: String,
    private val outputDirectory: Path,
) {

    fun generate(specs: List<ProtocolVersionSpec>): Map<ProtocolVersionSpec, GenerationReport> {
        val reports = specs.associateWith(::generateVersion)
        ProtocolCatalogEmitter(runtimePackage, registryPackage, outputDirectory).emit(specs)
        return reports
    }

    private fun generateVersion(spec: ProtocolVersionSpec): GenerationReport {
        val protocol = Json.parseToJsonElement(spec.protocolJson.toFile().readText()).jsonObject
        val options = PacketGenerationOptions(
            minecraftVersion = spec.minecraftVersion,
            protocolVersion = spec.protocolVersion,
            protocolResourcePath = spec.protocolResourcePath,
            runtimePackage = runtimePackage,
            packetPackage = "$packetPackage.${spec.packageSuffix}",
            registryPackage = "$registryPackage.${spec.packageSuffix}",
            serverboundPrefix = serverboundPrefix,
            clientboundPrefix = clientboundPrefix,
            outputDirectory = outputDirectory,
        )

        val naming = PacketNaming()
        val typeResolver = ProtocolTypeResolver(protocol, options, naming)
        val extractor = ProtocolPacketExtractor(protocol, options, naming, typeResolver)
        val emitter = KotlinPacketEmitter(options)

        val packets = extractor.extract()
        require(packets.isNotEmpty()) {
            "Minecraft ${spec.minecraftVersion} produced no packets. Its protocol.json does not use the " +
                "handshaking/status/login/configuration/play state layout this generator targets."
        }

        val registryEntries = packets.map(emitter::emitPacket)
        emitter.emitRegistry(registryEntries)

        return GenerationReport(
            packetCount = registryEntries.size,
            supportTypeCount = typeResolver.emittedTypeCount,
        )
    }
}
