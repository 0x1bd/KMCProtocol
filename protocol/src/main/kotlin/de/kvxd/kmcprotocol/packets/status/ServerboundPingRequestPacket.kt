package de.kvxd.kmcprotocol.packets.status

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.PacketMetadata
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.Serializable

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.Serverbound
)
data object ServerboundPingRequestPacket : MinecraftPacket