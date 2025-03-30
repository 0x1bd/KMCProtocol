package de.kvxd.kmcprotocol.network

import com.kvxd.eventbus.Event
import de.kvxd.kmcprotocol.packet.MinecraftPacket

data class ConnectionError(val cause: Throwable) : Event
data class PacketReceived(val packet: MinecraftPacket) : Event
data object Disconnected : Event