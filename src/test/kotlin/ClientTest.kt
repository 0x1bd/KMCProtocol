
import de.kvxd.kmcprotocol.Direction
import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.datatypes.VarLong
import de.kvxd.kmcprotocol.network.TCPClient
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import de.kvxd.kmcprotocol.serialization.PacketSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

@Serializable
@PacketMetadata(
    id = 0x01,
    direction = Direction.C2S,
    state = ProtocolState.HANDSHAKE
)
data class TestPacket(
    @Serializable(with = VarLong.Serializer::class)
    val foo: Long,
    val bar: String
) : MinecraftPacket

class ClientTest {

    private val protocol = MinecraftProtocol()

    @Test
    fun testConnection() = runBlocking {
        val client = TCPClient("localhost", 25565)

        client.connect()

        val packet = TestPacket(
            69,
            "BOB"
        )

        protocol.registry = PacketRegistry.create(protocol) {
            registerPacket(TestPacket::class)
        }

        val serialized = PacketSerializer.serialize(protocol, packet)

        client.sendPacket(serialized)

        client.disconnect()
    }

}