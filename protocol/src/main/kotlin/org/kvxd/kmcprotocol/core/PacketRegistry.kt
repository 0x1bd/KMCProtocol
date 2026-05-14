package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import org.kvxd.kmcprotocol.generated.GeneratedPacketRegistry
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class PacketRegistry {

    private val packetsByState = mutableMapOf<ProtocolState, MutableList<RegisteredPacket>>()
    private val packetsById = mutableMapOf<ProtocolState, MutableMap<Direction, MutableMap<Int, RegisteredPacket>>>()
    private val packetsByClass = mutableMapOf<ProtocolState, MutableMap<KClass<out MinecraftPacket>, RegisteredPacket>>()

    data class RegisteredPacket(
        val id: Int,
        val direction: Direction,
        val packetClass: KClass<out MinecraftPacket>,
        val serializer: KSerializer<out MinecraftPacket>
    )

    @OptIn(InternalSerializationApi::class)
    fun registerStatePackets(state: ProtocolState) {
        GeneratedPacketRegistry.packetsById(state).forEach { (direction, packetsById) ->
            packetsById.forEach { (id, packetClass) ->
                registerPacket(
                    state,
                    RegisteredPacket(
                        id = id,
                        direction = direction,
                        packetClass = packetClass,
                        serializer = packetClass.serializer(),
                    )
                )
            }
        }
    }

    private fun registerPacket(state: ProtocolState, packet: RegisteredPacket) {
        val directionPackets = packetsById
            .computeIfAbsent(state) { mutableMapOf() }
            .computeIfAbsent(packet.direction) { mutableMapOf() }

        val packetById = directionPackets.putIfAbsent(packet.id, packet)
        require(packetById == null) {
            "Duplicate packet id 0x${packet.id.toString(16)} for ${packet.direction} in $state: " +
                "${packetById!!.packetClass.qualifiedName} and ${packet.packetClass.qualifiedName}"
        }

        val classPackets = packetsByClass.computeIfAbsent(state) { mutableMapOf() }
        val packetByClass = classPackets.putIfAbsent(packet.packetClass, packet)
        require(packetByClass == null) {
            "Duplicate packet class ${packet.packetClass.qualifiedName} in $state"
        }

        packetsByState.computeIfAbsent(state) { mutableListOf() }.add(packet)
    }

    fun getPacketSerializerById(state: ProtocolState, id: Int, direction: Direction): KSerializer<MinecraftPacket>? {
        return packetsById[state]?.get(direction)?.get(id)?.serializer as KSerializer<MinecraftPacket>?
    }

    fun getPacketSerializerByClass(
        state: ProtocolState,
        packetClass: KClass<out MinecraftPacket>
    ): KSerializer<MinecraftPacket>? {
        return packetsByClass[state]?.get(packetClass)?.serializer as KSerializer<MinecraftPacket>?
    }

    fun getIdFromPacketClass(state: ProtocolState, packetClass: KClass<out MinecraftPacket>): Int? {
        return packetsByClass[state]?.get(packetClass)?.id
    }

    fun getPacketMetadata(state: ProtocolState, id: Int, direction: Direction): RegisteredPacket? {
        return packetsById[state]?.get(direction)?.get(id)
    }

    fun getPacketMetadata(state: ProtocolState, packet: MinecraftPacket): RegisteredPacket? {
        val packetClass = packet::class
        return packetsByClass[state]?.get(packetClass)
    }

    fun initializePacketRegistry() {
        ProtocolState.entries.forEach { state ->
            registerStatePackets(state)
        }
    }
}