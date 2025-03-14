
import de.kvxd.kmcprotocol.Direction
import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.network.KMCClient
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import de.kvxd.kmcprotocol.serialization.PacketSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import kotlin.test.Test

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.C2S,
    state = ProtocolState.HANDSHAKE
)
data class TestPacket(
    @Contextual
    val foo: Component
) : MinecraftPacket

class ClientTest {

    private val protocol = MinecraftProtocol()

    @Test
    fun testConnection() = runBlocking {
        val client = KMCClient("localhost", 25565)

        client.connect()

        val packet = TestPacket(
            Component.text("Hello, World")
        )

        protocol.registry = PacketRegistry.create(protocol) {
            registerPacket(TestPacket::class)
        }

        val serialized = PacketSerializer.serialize(protocol, packet)

        client.sendPacket(serialized)

        client.disconnect()
    }

}