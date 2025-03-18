import de.kvxd.kmcprotocol.datatypes.readString
import de.kvxd.kmcprotocol.datatypes.readVarInt
import de.kvxd.kmcprotocol.packets.FooPacket
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPacketCodec {

    @Test
    fun `test varInt codec`() {
        val packet = FooPacket(42, "Hello, World!")

        val channel = ByteChannel()

        packet.codec.encode(channel)

        val decoded = packet.codec.decode(channel)

        assertEquals(packet, decoded)
    }

}