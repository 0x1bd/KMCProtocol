package de.kvxd.kmcprotocol.registry

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

class PacketRegistry(val protocol: MinecraftProtocol) {

    private val packets = mutableMapOf<KClass<out MinecraftPacket<*>>, PacketMetadata>()

    fun registerPacket(packetKClass: KClass<out MinecraftPacket<*>>) {
        val annotations = packetKClass.findAnnotations<PacketMetadata>()

        if (annotations.isEmpty()) {
            throw IllegalStateException("Packet class $packetKClass is missing PacketMetadata annotation")
        }

        packets[packetKClass] = annotations.firstOrNull { metadata ->
            metadata.state == protocol.state
        } ?: throw IllegalStateException("Packet $packetKClass does not support state ${protocol.state}. Expecting ${annotations.first().state}")
    }

    fun getPacketMetadata(packetKClass: KClass<out MinecraftPacket<*>>): PacketMetadata {
        val metadata = packets[packetKClass] ?: throw IllegalArgumentException("Packet $packetKClass has not been registered.")

        if (metadata.state != protocol.state) throw IllegalStateException("Protocol state ${protocol.state} does not match the expected state for packet $packetKClass")

        return metadata
    }

    fun getPacketID(packetKClass: KClass<out MinecraftPacket<*>>): Int {
        return getPacketMetadata(packetKClass).id
    }

    fun getPacketClassById(id: Int): KClass<out MinecraftPacket<*>> {
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