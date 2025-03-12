package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class PacketRegistry(val protocol: MinecraftProtocol) {

    private val packets = mutableMapOf<KClass<out MinecraftPacket>, PacketMetadata>()

    fun registerPacket(packetKClass: KClass<out MinecraftPacket>) {
        packets[packetKClass] = getPacketMetadata(packetKClass)
    }

    fun getPacketMetadata(packetKClass: KClass<out MinecraftPacket>): PacketMetadata {
        val annotations = packetKClass.findAnnotations<PacketMetadata>()

        if (annotations.isEmpty()) {
            throw IllegalStateException("Packet class $packetKClass is missing PacketMetadata annotation")
        }

        return annotations.firstOrNull { metadata ->
            metadata.state == protocol.state
        } ?: throw IllegalStateException("Packet $packetKClass does not support state ${protocol.state}. Expecting ${annotations.first().state}")
    }

    fun getPacketID(packetKClass: KClass<out MinecraftPacket>): Int {
        return getPacketMetadata(packetKClass).id
    }

    fun getPacketClassById(id: Int): KClass<out MinecraftPacket> {
        return packets.filter { it.value.id == id }.entries.first().key
    }

    companion object {

        fun create(protocol: MinecraftProtocol, init: PacketRegistry.() -> Unit): PacketRegistry {
            val registry = PacketRegistry(protocol)
            registry.init()
            return registry
        }
    }

}