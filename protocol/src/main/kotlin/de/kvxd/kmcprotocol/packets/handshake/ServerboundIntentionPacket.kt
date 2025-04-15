package de.kvxd.kmcprotocol.packets.handshake

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.PacketMetadata
import de.kvxd.kmcprotocol.core.ProtocolState
import de.kvxd.kmcprotocol.core.format.EnumValue
import de.kvxd.kmcprotocol.core.format.number.IntFormat
import de.kvxd.kmcprotocol.core.format.number.IntFormatType
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.Serializable

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.Serverbound
)
data class ServerboundIntentionPacket(
    @IntFormat(IntFormatType.VARIABLE)
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: UShort,
    val nextState: NextState
) : MinecraftPacket {

    @IntFormat(IntFormatType.VARIABLE)
    enum class NextState(val protocolState: ProtocolState) {

        @EnumValue(1)
        Status(ProtocolState.Status),

        @EnumValue(2)
        Login(ProtocolState.Login),

        @EnumValue(3)
        Transfer(ProtocolState.Login);
    }

}