import de.kvxd.kmcprotocol.PacketSerializer
import kotlin.test.*

class HandshakePacketTest {
    @Test
    fun testHandshakePacketSerialization() {
        val packet = HandshakePacket(
            protocolVersion = 754,
            serverAddress = "localhost",
            serverPort = 25565,
            nextState = 1
        )

        val serialized = PacketSerializer.serialize(packet)

        val deserialized = PacketSerializer.deserialize<HandshakePacket>(serialized)

        assertEquals(packet, deserialized)
        assertEquals(754, deserialized.protocolVersion)
        assertEquals("localhost", deserialized.serverAddress)
        assertEquals(25565, deserialized.serverPort)
        assertEquals(1, deserialized.nextState)
    }
}