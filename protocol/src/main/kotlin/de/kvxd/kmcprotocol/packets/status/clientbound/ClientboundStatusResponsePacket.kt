package de.kvxd.kmcprotocol.packets.status.clientbound

import de.kvxd.kmcprotocol.LATEST_PROTOCOL_NAME
import de.kvxd.kmcprotocol.LATEST_PROTOCOL_VERSION
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.jsonStringCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import net.kyori.adventure.text.Component

data class StatusResponse(
    val version: StatusVersion = StatusVersion(LATEST_PROTOCOL_NAME, LATEST_PROTOCOL_VERSION),
    val players: StatusPlayers = StatusPlayers(20, 0, listOf()),
    val description: Component = Component.text("A Minecraft Server"),
    val favicon: String? = "",
    val enforcesSecureChat: Boolean? = false
)

data class StatusVersion(val name: String, val protocolVersion: Int)

data class StatusPlayers(val max: Int, val online: Int, val sample: List<Player>) {

    data class Player(val name: String, val id: String)
}

@PacketMetadata(
    id = 0x00,
    direction = Direction.CLIENTBOUND,
    state = ProtocolState.STATUS,
)
data class ClientboundStatusResponsePacket(
    val response: StatusResponse
) : MinecraftPacket {

    companion object {

        val CODEC = PacketCodec<ClientboundStatusResponsePacket> {
            element(ClientboundStatusResponsePacket::response, jsonStringCodec())
        }
    }

}