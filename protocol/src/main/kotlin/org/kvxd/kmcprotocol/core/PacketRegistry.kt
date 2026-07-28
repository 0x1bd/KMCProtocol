package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

class PacketRegistry(
    sources: Iterable<PacketRegistrySource> = emptyList(),
) {
    private val packetsById = mutableMapOf<ProtocolState, MutableMap<Direction, MutableMap<Int, RegisteredPacket>>>()
    private val packetsByClass = mutableMapOf<ProtocolState, MutableMap<KClass<out MinecraftPacket>, RegisteredPacket>>()

    data class RegisteredPacket(
        val id: Int,
        val direction: Direction,
        val packetClass: KClass<out MinecraftPacket>,
        val serializer: KSerializer<MinecraftPacket>,
    )

    init {
        sources.forEach(::registerSource)
    }

    fun registerSource(source: PacketRegistrySource) {
        ProtocolState.entries.forEach { state -> registerStatePackets(state, source) }
    }

    fun registerStatePackets(state: ProtocolState, source: PacketRegistrySource) {
        source.packetsById(state).forEach { (direction, packets) ->
            packets.forEach { (id, packetClass) ->
                registerPacket(state, RegisteredPacket(id, direction, packetClass, packetClass.packetSerializer()))
            }
        }
    }

    fun getPacketSerializerById(state: ProtocolState, id: Int, direction: Direction): KSerializer<MinecraftPacket>? =
        packetsById[state]?.get(direction)?.get(id)?.serializer

    fun getPacketSerializerByClass(
        state: ProtocolState,
        packetClass: KClass<out MinecraftPacket>,
    ): KSerializer<MinecraftPacket>? = packetsByClass[state]?.get(packetClass)?.serializer

    fun getIdFromPacketClass(state: ProtocolState, packetClass: KClass<out MinecraftPacket>): Int? =
        packetsByClass[state]?.get(packetClass)?.id

    fun getPacketMetadata(state: ProtocolState, id: Int, direction: Direction): RegisteredPacket? =
        packetsById[state]?.get(direction)?.get(id)

    fun getPacketMetadata(state: ProtocolState, packet: MinecraftPacket): RegisteredPacket? =
        packetsByClass[state]?.get(packet::class)

    private fun registerPacket(state: ProtocolState, packet: RegisteredPacket) {
        val existingById = packetsById
            .getOrPut(state) { mutableMapOf() }
            .getOrPut(packet.direction) { mutableMapOf() }
            .putIfAbsent(packet.id, packet)
        require(existingById == null) {
            "Duplicate packet id 0x${packet.id.toString(16)} for ${packet.direction} in $state: " +
                "${existingById?.packetClass?.qualifiedName} and ${packet.packetClass.qualifiedName}"
        }

        val existingByClass = packetsByClass
            .getOrPut(state) { mutableMapOf() }
            .putIfAbsent(packet.packetClass, packet)
        require(existingByClass == null) {
            "Duplicate packet class ${packet.packetClass.qualifiedName} in $state"
        }
    }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun KClass<out MinecraftPacket>.packetSerializer(): KSerializer<MinecraftPacket> =
        serializer() as KSerializer<MinecraftPacket>
}
