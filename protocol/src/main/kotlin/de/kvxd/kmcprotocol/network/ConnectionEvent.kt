package de.kvxd.kmcprotocol.network

import de.kvxd.kmcprotocol.core.MinecraftPacket

sealed class ConnectionEvent {

    data class PacketReceived(val packet: MinecraftPacket) : ConnectionEvent()

    data object Disconnected : ConnectionEvent()
    data class ConnectionError(val exception: Throwable) : ConnectionEvent()
}
