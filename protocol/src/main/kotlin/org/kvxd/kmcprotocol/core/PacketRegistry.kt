package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * Mutable packet registry used to resolve packet ids, packet classes, and serializers.
 */
class PacketRegistry(
    /**
     * Registry sources to load during construction.
     */
    sources: Iterable<PacketRegistrySource> = emptyList(),
) {

    private val packetsByState = mutableMapOf<ProtocolState, MutableList<RegisteredPacket>>()
    private val packetsById = mutableMapOf<ProtocolState, MutableMap<Direction, MutableMap<Int, RegisteredPacket>>>()
    private val packetsByClass = mutableMapOf<ProtocolState, MutableMap<KClass<out MinecraftPacket>, RegisteredPacket>>()

    /**
     * Registered packet metadata for a single protocol state and direction.
     */
    data class RegisteredPacket(
        /**
         * Numeric packet id in the containing protocol state.
         */
        val id: Int,

        /**
         * Direction in which the packet travels.
         */
        val direction: Direction,

        /**
         * Packet implementation class.
         */
        val packetClass: KClass<out MinecraftPacket>,

        /**
         * Serializer for the packet implementation class.
         */
        val serializer: KSerializer<out MinecraftPacket>
    )

    init {
        sources.forEach(::registerSource)
    }

    /**
     * Registers packets from a source for every protocol state.
     */
    @OptIn(InternalSerializationApi::class)
    fun registerSource(source: PacketRegistrySource) {
        ProtocolState.entries.forEach { state ->
            source.packetsById(state).forEach { (direction, packetsById) ->
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
    }

    /**
     * Registers packets from a source for a single protocol state.
     */
    @OptIn(InternalSerializationApi::class)
    fun registerStatePackets(state: ProtocolState, source: PacketRegistrySource) {
        source.packetsById(state).forEach { (direction, packetsById) ->
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
        if (packetById != null) {
            throw IllegalArgumentException(
                "Duplicate packet id 0x${packet.id.toString(16)} for ${packet.direction} in $state: " +
                    "${packetById.packetClass.qualifiedName} and ${packet.packetClass.qualifiedName}"
            )
        }

        val classPackets = packetsByClass.computeIfAbsent(state) { mutableMapOf() }
        val packetByClass = classPackets.putIfAbsent(packet.packetClass, packet)
        require(packetByClass == null) {
            "Duplicate packet class ${packet.packetClass.qualifiedName} in $state"
        }

        packetsByState.computeIfAbsent(state) { mutableListOf() }.add(packet)
    }

    /**
     * Returns the packet serializer for a state, id, and direction, or null when no packet is registered.
     */
    fun getPacketSerializerById(state: ProtocolState, id: Int, direction: Direction): KSerializer<MinecraftPacket>? {
        return packetsById[state]?.get(direction)?.get(id)?.serializer as KSerializer<MinecraftPacket>?
    }

    /**
     * Returns the packet serializer for a state and packet class, or null when no packet is registered.
     */
    fun getPacketSerializerByClass(
        state: ProtocolState,
        packetClass: KClass<out MinecraftPacket>
    ): KSerializer<MinecraftPacket>? {
        return packetsByClass[state]?.get(packetClass)?.serializer as KSerializer<MinecraftPacket>?
    }

    /**
     * Returns the registered packet id for a state and packet class, or null when no packet is registered.
     */
    fun getIdFromPacketClass(state: ProtocolState, packetClass: KClass<out MinecraftPacket>): Int? {
        return packetsByClass[state]?.get(packetClass)?.id
    }

    /**
     * Returns registered packet metadata for a state, id, and direction.
     */
    fun getPacketMetadata(state: ProtocolState, id: Int, direction: Direction): RegisteredPacket? {
        return packetsById[state]?.get(direction)?.get(id)
    }

    /**
     * Returns registered packet metadata for a packet instance in the supplied state.
     */
    fun getPacketMetadata(state: ProtocolState, packet: MinecraftPacket): RegisteredPacket? {
        val packetClass = packet::class
        return packetsByClass[state]?.get(packetClass)
    }

}
