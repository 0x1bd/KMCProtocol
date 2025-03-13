import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.network.TCPClient
import de.kvxd.kmcprotocol.registry.PacketRegistry
import de.kvxd.kmcprotocol.serialization.PacketSerializer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ClientTest {

    private val protocol = MinecraftProtocol()

    @Test
    fun testConnection() = runBlocking {
        val client = TCPClient("localhost", 25565)

        client.connect()

        val packet = HandshakePacket(
            protocolVersion = 754,
            serverAddress = "localhost",
            serverPort = 25565,
            nextState = 1
        )

        protocol.registry = PacketRegistry.create(protocol) {
            registerPacket(HandshakePacket::class)
        }

        val serialized = PacketSerializer.serialize(protocol, packet)

        client.sendPacket(serialized)

        client.disconnect()
    }

}