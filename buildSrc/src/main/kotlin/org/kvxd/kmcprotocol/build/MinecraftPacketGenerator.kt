package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.JsonObject

internal class MinecraftPacketGenerator(
    private val protocol: JsonObject,
    private val options: PacketGenerationOptions,
) {
    fun generate(): GenerationReport {
        val naming = PacketNaming()
        val typeResolver = ProtocolTypeResolver(protocol, options, naming)
        val extractor = ProtocolPacketExtractor(protocol, options, naming, typeResolver)
        val emitter = KotlinPacketEmitter(options)

        val registryEntries = extractor.extract().map { packet ->
            emitter.emitPacket(packet)
        }
        emitter.emitRegistry(registryEntries)

        return GenerationReport(
            packetCount = registryEntries.size,
            supportTypeCount = typeResolver.emittedTypeCount,
        )
    }
}
