package de.kvxd.kmcprotocol.packets.status.clientbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.LongCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

@PacketMetadata(
    id = 0x01,
    direction = Direction.CLIENTBOUND,
    state = ProtocolState.STATUS,
)
data class ClientboundPongResponsePacket(
    val timestamp: Long
) : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ClientboundPongResponsePacket> {
            element(ClientboundPongResponsePacket::timestamp, LongCodec)
        }
    }

}