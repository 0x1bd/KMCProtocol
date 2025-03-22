package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class PacketRegistry(private val protocol: MinecraftProtocol) {
    private val classToEntry = mutableMapOf<KClass<out MinecraftPacket>, PacketEntry>()
    private val idToEntry = mutableMapOf<Int, PacketEntry>()

    fun registerPacket(packetClass: KClass<out MinecraftPacket>, codec: PacketCodec<out MinecraftPacket>) {
        val metadata = packetClass.findAnnotations<PacketMetadata>()
            .firstOrNull { it.state == protocol.state }
            ?: throw IllegalStateException("Packet ${packetClass.simpleName} missing valid metadata for state ${protocol.state}")

        require(!idToEntry.containsKey(metadata.id)) {
            "Duplicate packet ID ${metadata.id} (${packetClass.simpleName}) in state ${protocol.state}"
        }

        val entry = PacketEntry(metadata, codec)
        classToEntry[packetClass] = entry
        idToEntry[metadata.id] = entry
    }

    fun getPacketData(packet: MinecraftPacket): Triple<Int, PacketCodec<*>, PacketMetadata> {
        val entry = classToEntry[packet::class] ?: throw missingError(packet)
        return Triple(entry.metadata.id, entry.codec, entry.metadata)
    }

    fun getPacketDataById(id: Int): Pair<PacketCodec<*>, PacketMetadata> {
        val entry = idToEntry[id] ?: throw IllegalArgumentException("No packet registered with ID $id")
        return Pair(entry.codec, entry.metadata)
    }

    private fun missingError(packet: MinecraftPacket): Nothing {
        val available = classToEntry.keys.joinToString { it.simpleName ?: "Unknown" }
        throw IllegalArgumentException(
            "Packet ${packet::class.simpleName} not registered. Available: $available"
        )
    }

    companion object {
        fun create(protocol: MinecraftProtocol, init: PacketRegistry.() -> Unit): PacketRegistry {
            return PacketRegistry(protocol).apply(init)
        }
    }

    private data class PacketEntry(
        val metadata: PacketMetadata,
        val codec: PacketCodec<out MinecraftPacket>
    )
}