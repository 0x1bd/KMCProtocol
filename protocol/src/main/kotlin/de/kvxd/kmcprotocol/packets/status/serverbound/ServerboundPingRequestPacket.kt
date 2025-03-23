package de.kvxd.kmcprotocol.packets.status.serverbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.LongCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

@PacketMetadata(
    id = 0x01,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.STATUS,
)
data class ServerboundPingRequestPacket(
    val timestamp: Long
) : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ServerboundPingRequestPacket> {
            element(ServerboundPingRequestPacket::timestamp, LongCodec)
        }
    }

}