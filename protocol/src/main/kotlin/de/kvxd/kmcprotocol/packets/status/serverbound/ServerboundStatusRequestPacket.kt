package de.kvxd.kmcprotocol.packets.status.serverbound

import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.STATUS,
)
class ServerboundStatusRequestPacket : MinecraftPacket {

    companion object {
        val CODEC = PacketCodec<ServerboundStatusRequestPacket> { }
    }

}