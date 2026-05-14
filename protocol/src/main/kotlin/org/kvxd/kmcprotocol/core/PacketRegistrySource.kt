package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlin.reflect.KClass

/**
 * Source of packet classes grouped by protocol state, direction, and packet id.
 */
interface PacketRegistrySource {
    /**
     * Returns packet classes for the supplied protocol state.
     */
    fun packetsById(state: ProtocolState): Map<Direction, Map<Int, KClass<out MinecraftPacket>>>
}
