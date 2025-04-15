package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class PacketRegistry {

    private val registry = mutableMapOf<KClass<out MinecraftPacket>, PacketInfo>()

    data class PacketInfo(
        val metadata: PacketMetadata,
        val state: ProtocolState,
        val serializer: KSerializer<out MinecraftPacket>
    )

    fun register(packetClass: KClass<out MinecraftPacket>, state: ProtocolState) {
        val metadata = packetClass.java.getAnnotation(PacketMetadata::class.java)
            ?: throw IllegalArgumentException("Packet class ${packetClass.simpleName} must have @PacketMetadata annotation")

        registry[packetClass] = PacketInfo(metadata, state, serializer())
    }

    fun getPacketInfo(packetClass: KClass<out MinecraftPacket>): PacketInfo {
        println("registry (${registry.size})")
        println(registry.toList().joinToString { "${it.first} : ${it.second}" })
        return registry[packetClass] ?: throw IllegalArgumentException("Packet class not registered")
    }

    fun getPacketClass(id: Int, direction: Direction): KClass<out MinecraftPacket> {
        return registry.entries.firstOrNull {
            it.value.metadata.id == id &&
                    it.value.metadata.direction == direction
        }?.key ?: throw IllegalArgumentException("No packet registered for ID $id and direction $direction")
    }

}