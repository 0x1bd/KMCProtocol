package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class PacketRegistry(private val protocol: MinecraftProtocol) {

    private val packets = mutableMapOf<KClass<out MinecraftPacket>, PacketEntry>()

    fun registerPacket(packetKClass: KClass<out MinecraftPacket>, codec: PacketCodec<out MinecraftPacket>) {
        val annotations = packetKClass.findAnnotations<PacketMetadata>()

        if (annotations.isEmpty()) {
            throw IllegalStateException("Packet class $packetKClass is missing PacketMetadata annotation")
        }

        val metadata = annotations.firstOrNull { it.state == protocol.state }
            ?: throw IllegalStateException("Packet $packetKClass does not support state ${protocol.state}. Expecting ${annotations.first().state}")

        packets[packetKClass] = PacketEntry(metadata, codec)
    }

    fun getPacketMetadata(packetKClass: KClass<out MinecraftPacket>): PacketMetadata {
        val entry =
            packets[packetKClass] ?: throw IllegalArgumentException("Packet $packetKClass has not been registered.")
        if (entry.metadata.state != protocol.state) throw IllegalStateException("Protocol state ${protocol.state} does not match the expected state for packet $packetKClass")
        return entry.metadata
    }

    fun getPacketID(packetKClass: KClass<out MinecraftPacket>): Int {
        return getPacketMetadata(packetKClass).id
    }

    fun getPacketClassById(id: Int): KClass<out MinecraftPacket> {
        return packets.filter { it.value.metadata.id == id }.entries.first().key
    }

    fun getCodec(packetKClass: KClass<out MinecraftPacket>): PacketCodec<out MinecraftPacket> {
        val entry =
            packets[packetKClass] ?: throw IllegalArgumentException("Packet $packetKClass has not been registered.")
        return entry.codec
    }

    fun getCodecFromId(id: Int): PacketCodec<out MinecraftPacket> {
        return packets.filter { it.value.metadata.id == id }.entries.first().value.codec
    }

    companion object {
        fun create(protocol: MinecraftProtocol, init: PacketRegistry.() -> Unit): PacketRegistry {
            val registry = PacketRegistry(protocol)
            registry.init()
            return registry
        }
    }

    private data class PacketEntry(
        val metadata: PacketMetadata,
        val codec: PacketCodec<out MinecraftPacket>
    )
}