import de.kvxd.kmcprotocol.Packet
import de.kvxd.kmcprotocol.datatypes.varint.VarIntSerializer
import kotlinx.serialization.Serializable

@Serializable
data class HandshakePacket(
    @Serializable(with = VarIntSerializer::class)
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Short,
    @Serializable(with = VarIntSerializer::class)
    val nextState: Int
) : Packet {
    override val packetId: Int = 0x00
}