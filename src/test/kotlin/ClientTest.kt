
import de.kvxd.kmcprotocol.Direction
import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.datatypes.Vec3
import de.kvxd.kmcprotocol.network.KMCClient
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import de.kvxd.kmcprotocol.serialization.PacketSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.C2S,
    state = ProtocolState.HANDSHAKE
)
data class TestPacket(
    val foo: Vec3
) : MinecraftPacket

class ClientTest {

    private val protocol = MinecraftProtocol()

    @Test
    fun testConnection() = runBlocking {
        val client = KMCClient("localhost", 25565)

        client.connect()

        val packet = TestPacket(
            Vec3(0.0, 1.0, 65.0)
        )

        protocol.registry = PacketRegistry.create(protocol) {
            registerPacket(TestPacket::class)
        }

        val serialized = PacketSerializer.serialize(protocol, packet)

        client.sendPacket(serialized)

        client.disconnect()
    }

}