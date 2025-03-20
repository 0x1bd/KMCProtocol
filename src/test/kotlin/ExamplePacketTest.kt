
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.ProtocolState
import de.kvxd.kmcprotocol.codecs.string
import de.kvxd.kmcprotocol.codecs.varInt
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.HANDSHAKE
)
data class ExamplePacket(
    var bar: Int,
    var foobar: String
) : MinecraftPacket {

    companion object {

        val CODEC = PacketCodec<ExamplePacket> {
            varInt(ExamplePacket::bar)
            string(ExamplePacket::foobar)
        }
    }

}

class ExamplePacketTest {

    @Test
    fun `test example packet codec`() = runBlocking {
        val packet = ExamplePacket(42, "Hello, World!")

        val channel = ByteChannel()

        ExamplePacket.CODEC.encode(packet, channel)

        val decoded = ExamplePacket.CODEC.decode(channel)

        assertEquals(packet, decoded)
    }

    @Test
    fun `test example packet codec with registry`() = runBlocking {
        val protocol = MinecraftProtocol()

        val registry = PacketRegistry.create(protocol) {
            registerPacket(ExamplePacket::class, ExamplePacket.CODEC)
        }

        val packet = ExamplePacket(42, "Hello, World!")

        val channel = ByteChannel()

        registry.getCodecFromId(0).encode(packet, channel)

        val decoded = registry.getCodecFromId(0).decode(channel)

        assertEquals(packet, decoded)
    }

}