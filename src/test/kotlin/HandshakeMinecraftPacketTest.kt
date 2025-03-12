import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.registry.PacketRegistry
import de.kvxd.kmcprotocol.serialization.PacketSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class HandshakeMinecraftPacketTest {

    private val protocol = MinecraftProtocol()

    @Test
    fun testHandshakePacketSerialization() {
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

        val deserialized = PacketSerializer.deserialize(protocol, serialized) as HandshakePacket

        assertEquals(packet, deserialized)
        assertEquals(754, deserialized.protocolVersion)
        assertEquals("localhost", deserialized.serverAddress)
        assertEquals(25565, deserialized.serverPort)
        assertEquals(1, deserialized.nextState)
    }
}