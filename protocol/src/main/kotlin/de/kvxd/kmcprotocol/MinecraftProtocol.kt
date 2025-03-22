package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class MinecraftProtocol(initPacketRegistry: PacketRegistry.() -> Unit) {

    var state: ProtocolState = ProtocolState.HANDSHAKE
    var direction: Direction = Direction.SERVERBOUND

    var registry: PacketRegistry

    init {
        registry = PacketRegistry().apply(initPacketRegistry)
    }

    inner class PacketRegistry {

        private val classToEntry = mutableMapOf<KClass<out MinecraftPacket>, PacketEntry>()
        private val idDirectionToEntry = mutableMapOf<Pair<Int, Direction>, PacketEntry>()

        fun registerPacket(packetClass: KClass<out MinecraftPacket>, codec: PacketCodec<out MinecraftPacket>) {
            val metadata = packetClass.findAnnotations<PacketMetadata>()
                .firstOrNull { it.state == state }
                ?: throw IllegalStateException("Packet ${packetClass.simpleName} missing valid metadata for state $state")

            val key = metadata.id to metadata.direction
            require(!idDirectionToEntry.containsKey(key)) {
                "Duplicate packet ID ${metadata.id} with direction ${metadata.direction} (${packetClass.simpleName}) in state $state"
            }

            val entry = PacketEntry(metadata, codec)
            classToEntry[packetClass] = entry
            idDirectionToEntry[key] = entry
        }

        fun getPacketData(packet: MinecraftPacket): Triple<Int, PacketCodec<*>, PacketMetadata> {
            val entry = classToEntry[packet::class] ?: throw missingError(packet)
            return Triple(entry.metadata.id, entry.codec, entry.metadata)
        }

        fun getPacketDataById(id: Int, direction: Direction): Pair<PacketCodec<*>, PacketMetadata> {
            val key = id to direction
            val entry = idDirectionToEntry[key]
                ?: throw IllegalArgumentException("No packet registered with ID $id and direction $direction")
            return Pair(entry.codec, entry.metadata)
        }

        fun getPacketDataById(id: Int): Pair<PacketCodec<*>, PacketMetadata> {
            return getPacketDataById(id, direction)
        }

        private fun missingError(packet: MinecraftPacket): Nothing {
            val available = classToEntry.keys.joinToString { it.simpleName ?: "Unknown" }
            throw IllegalArgumentException(
                "Packet ${packet::class.simpleName} not registered. Available: $available"
            )
        }

        inner class PacketEntry(
            val metadata: PacketMetadata,
            val codec: PacketCodec<out MinecraftPacket>
        )
    }

}