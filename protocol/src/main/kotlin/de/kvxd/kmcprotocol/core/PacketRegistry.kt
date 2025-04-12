package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class PacketRegistry {

    private val statePacketMap = mutableMapOf<ProtocolState, MutableList<RegisteredPacket>>()

    data class RegisteredPacket(
        val id: Int,
        val direction: Direction,
        val packetClass: KClass<out MinecraftPacket>,
        val serializer: KSerializer<out MinecraftPacket>
    )

    @OptIn(InternalSerializationApi::class)
    fun registerStatePackets(state: ProtocolState) {
        state.packets.forEach { packetClass ->
            val metadata = packetClass.findAnnotation<PacketMetadata>()
                ?: throw IllegalArgumentException("Packet class ${packetClass.simpleName} must have a PacketMetadata annotation")

            val serializer = packetClass.serializer()
            val registeredPacket = RegisteredPacket(
                id = metadata.id,
                direction = metadata.direction,
                packetClass = packetClass,
                serializer = serializer
            )

            statePacketMap.computeIfAbsent(state) { mutableListOf() }.add(registeredPacket)
        }
    }

    fun getPacketSerializerById(state: ProtocolState, id: Int, direction: Direction): KSerializer<MinecraftPacket>? {
        return statePacketMap[state]?.firstOrNull { it.id == id && it.direction == direction }?.serializer as KSerializer<MinecraftPacket>?
    }

    fun getPacketSerializerByClass(
        state: ProtocolState,
        packetClass: KClass<out MinecraftPacket>
    ): KSerializer<MinecraftPacket>? {
        return statePacketMap[state]?.firstOrNull { it.packetClass == packetClass }?.serializer as KSerializer<MinecraftPacket>?
    }

    fun getIdFromPacketClass(state: ProtocolState, packetClass: KClass<out MinecraftPacket>): Int? {
        return statePacketMap[state]?.firstOrNull { it.packetClass == packetClass }?.id
    }

    fun getPacketMetadata(state: ProtocolState, id: Int, direction: Direction): RegisteredPacket? {
        return statePacketMap[state]?.firstOrNull { it.id == id && it.direction == direction }
    }

    fun getPacketMetadata(state: ProtocolState, packet: MinecraftPacket): RegisteredPacket? {
        val packetClass = packet::class
        return statePacketMap[state]?.firstOrNull { it.packetClass == packetClass }
    }

    fun initializePacketRegistry() {
        ProtocolState.entries.forEach { state ->
            registerStatePackets(state)
        }
    }
}