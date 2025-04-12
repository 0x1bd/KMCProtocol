package de.kvxd.kmcprotocol.packets.status

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.PacketMetadata
import de.kvxd.kmcprotocol.core.variant.UseJson
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component

@Serializable
@PacketMetadata(
    0x00,
    Direction.Serverbound
)
data class ClientboundStatusResponsePacket(@UseJson val response: StatusResponse) : MinecraftPacket

@Serializable
data class StatusResponse(
    val version: StatusVersion,
    val players: StatusPlayers,
    val description: Component,
    val favicon: String?,
    val enforcesSecureChat: Boolean?
) {

    companion object {

        val VANILLA = StatusResponse(
            StatusVersion("1.21.5", 770),
            StatusPlayers(20, 0, listOf()),
            Component.text("A Minecraft Server"),
            null,
            null
        )
    }

}

@Serializable
data class StatusVersion(val name: String, val protocol: Int)

@Serializable
data class StatusPlayers(val max: Int, val online: Int, val sample: List<Player>) {

    @Serializable
    data class Player(val name: String, val id: String)
}