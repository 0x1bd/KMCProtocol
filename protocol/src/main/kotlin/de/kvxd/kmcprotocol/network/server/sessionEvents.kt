package de.kvxd.kmcprotocol.network.server

import com.kvxd.eventbus.Event
import de.kvxd.kmcprotocol.packet.MinecraftPacket

class SPacketReceived(val packet: MinecraftPacket) : Event

class SConnectionClosed : Event