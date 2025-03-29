package de.kvxd.kmcprotocol.packets.handshake.serverbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.StringCodec
import de.kvxd.kmcprotocol.codec.codecs.UShortCodec
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.codec.codecs.enumCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

enum class NextState {
    Status,
    Login,
    Transfer
}

fun NextState.toProtocolState(): ProtocolState {
    return when (this) {
        NextState.Status -> ProtocolState.STATUS
        NextState.Login -> ProtocolState.LOGIN
        NextState.Transfer -> ProtocolState.LOGIN
    }
}

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.HANDSHAKE,
)
data class ServerboundHandshakePacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Int,
    val nextState: NextState
) : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ServerboundHandshakePacket> {
            element(ServerboundHandshakePacket::protocolVersion, VarIntCodec)
            element(ServerboundHandshakePacket::serverAddress, StringCodec)
            element(ServerboundHandshakePacket::serverPort, UShortCodec)
            element(ServerboundHandshakePacket::nextState, enumCodec())
        }
    }

}