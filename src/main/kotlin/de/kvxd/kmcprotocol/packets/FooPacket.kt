package de.kvxd.kmcprotocol.packets

import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.packet.PacketCodec
import de.kvxd.kmcprotocol.registry.PacketMetadata

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.HANDSHAKE
)
data class FooPacket(
    var bar: Int,
    var foobar: String
): MinecraftPacket<FooPacket> {

    override val codec = PacketCodec(FooPacket::class) {
        varInt(::bar)
        string(::foobar)
    }

}