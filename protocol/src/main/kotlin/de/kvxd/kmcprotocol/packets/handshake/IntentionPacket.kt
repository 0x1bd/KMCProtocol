package de.kvxd.kmcprotocol.packets.handshake

import de.kvxd.kmcprotocol.core.MinecraftPacket

data class IntentionPacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Int,
    val nextState: NextState
) : MinecraftPacket {

    enum class NextState {
        Status,
        Login,
        Transfer
    }

}