package de.kvxd.kmcprotocol.network.client

import com.kvxd.eventbus.Event
import de.kvxd.kmcprotocol.packet.MinecraftPacket

class CConnected : Event
class CDisconnected(val reason: Reason) : Event {

    enum class Reason {
        ByServer,
        ByClient
    }
}

class CPacketReceived(val packet: MinecraftPacket) : Event
