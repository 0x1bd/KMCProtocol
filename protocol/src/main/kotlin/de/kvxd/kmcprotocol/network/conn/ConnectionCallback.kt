package de.kvxd.kmcprotocol.network.conn

import de.kvxd.kmcprotocol.core.MinecraftPacket

open class ConnectionCallback {

    open fun onPacketReceived(packet: MinecraftPacket) {}
    open fun onError(cause: Throwable) {
        cause.printStackTrace()
    }

    open fun onDisconnect() {}
}
