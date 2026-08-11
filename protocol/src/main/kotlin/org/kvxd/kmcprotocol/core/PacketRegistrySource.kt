package org.kvxd.kmcprotocol.core

import kotlinx.serialization.KSerializer
import org.kvxd.kmcprotocol.network.Direction
import kotlin.reflect.KClass

interface PacketRegistrySource {
    fun packetsById(state: ProtocolState): Map<Direction, Map<Int, KClass<out MinecraftPacket>>>

    fun serializerFor(packetClass: KClass<out MinecraftPacket>): KSerializer<out MinecraftPacket>? = null
}
