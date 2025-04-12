package de.kvxd.kmcprotocol.packets.handshake

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.PacketMetadata
import de.kvxd.kmcprotocol.core.variant.EValue
import de.kvxd.kmcprotocol.core.variant.EVariant
import de.kvxd.kmcprotocol.core.variant.NV
import de.kvxd.kmcprotocol.core.variant.NumVariant
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.Serializable

@PacketMetadata(
    0x00,
    Direction.Serverbound
)
@Serializable
data class IntentionPacket(
    @NV(NumVariant.VarInt)
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: UShort,
    val nextState: NextState
) : MinecraftPacket {

    @EVariant(NumVariant.VarInt)
    enum class NextState {
        @EValue(0)
        Status,

        @EValue(1)
        Login,

        @EValue(2)
        Transfer
    }

}