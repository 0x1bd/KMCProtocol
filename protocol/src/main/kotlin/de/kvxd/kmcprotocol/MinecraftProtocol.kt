package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketFormat
import de.kvxd.kmcprotocol.packet.PacketMetadata
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class MinecraftProtocol(initPacketRegistry: PacketRegistry.() -> Unit) {

    var state: ProtocolState = ProtocolState.HANDSHAKE

    var registry: PacketRegistry

    var packetFormat: PacketFormat = PacketFormat.Uncompressed

    init {
        registry = PacketRegistry().apply(initPacketRegistry)
    }

    inner class PacketRegistry {

        private val classToEntry = mutableMapOf<KClass<out MinecraftPacket>, PacketEntry>()
        private val compositeKeyToEntry = mutableMapOf<CompositeKey, PacketEntry>()

        fun registerPacket(packetClass: KClass<out MinecraftPacket>, codec: PacketCodec<out MinecraftPacket>) {
            val metadata = packetClass.findAnnotations<PacketMetadata>().firstOrNull()
                ?: throw IllegalStateException("Packet ${packetClass::class.simpleName} is missing packet metadata")

            val key = CompositeKey(metadata.id, metadata.direction, metadata.state)

            require(!compositeKeyToEntry.containsKey(key)) {
                "Duplicate packet ID ${metadata.id} with direction ${metadata.direction} and state ${metadata.state} (${packetClass.simpleName})"
            }

            val entry = PacketEntry(metadata, codec)
            classToEntry[packetClass] = entry
            compositeKeyToEntry[key] = entry
        }

        fun getPacketData(packet: MinecraftPacket): Pair<PacketCodec<*>, PacketMetadata> {
            val entry = classToEntry[packet::class] ?: throw missingError(packet)
            return Pair(entry.codec, entry.metadata)
        }

        fun getPacketDataById(
            id: Int,
            direction: Direction,
            state: ProtocolState
        ): Pair<PacketCodec<*>, PacketMetadata> {
            val key = CompositeKey(id, direction, state)
            val entry = compositeKeyToEntry[key]
                ?: throw IllegalArgumentException("No packet registered with ID $id, direction $direction, and state $state")
            return Pair(entry.codec, entry.metadata)
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

        inner class CompositeKey(
            val id: Int,
            val direction: Direction,
            val state: ProtocolState
        ) {

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as CompositeKey

                if (id != other.id) return false
                if (direction != other.direction) return false
                if (state != other.state) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id
                result = 31 * result + direction.hashCode()
                result = 31 * result + state.hashCode()
                return result
            }
        }
    }
}