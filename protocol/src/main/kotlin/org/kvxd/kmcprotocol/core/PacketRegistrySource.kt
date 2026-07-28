package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlin.reflect.KClass

interface PacketRegistrySource {
    fun packetsById(state: ProtocolState): Map<Direction, Map<Int, KClass<out MinecraftPacket>>>
}
