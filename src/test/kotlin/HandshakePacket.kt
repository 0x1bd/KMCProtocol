
import de.kvxd.kmcprotocol.Direction
import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.registry.PacketMetadata
import kotlinx.serialization.Serializable

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.C2S,
    state = ProtocolState.HANDSHAKE
)
data class HandshakePacket(
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Short,
    val nextState: Int
) : MinecraftPacket