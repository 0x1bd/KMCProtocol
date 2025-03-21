package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codecs.*
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import de.kvxd.kmcprotocol.registry.PacketMetadata
import de.kvxd.kmcprotocol.registry.PacketRegistry
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.HANDSHAKE
)
data class ExamplePacket(
    var boolean: Boolean,
    var byte: Byte,
    var double: Double,
    var float: Float,
    var int: Int,
    var long: Long,
    var short: Short,
    var string: String,
    var varInt: Int,
    var varLong: Long,
    var uuid: UUID,
    var jsonComponent: Component,
    var nbtComponent: Component,
    var styledNbtComponent: Component,
    var position: Vec3i,
) : MinecraftPacket {

    companion object {

        val CODEC = PacketCodec<ExamplePacket> {
            boolean(ExamplePacket::boolean)
            byte(ExamplePacket::byte)
            double(ExamplePacket::double)
            float(ExamplePacket::float)
            int(ExamplePacket::int)
            long(ExamplePacket::long)
            short(ExamplePacket::short)
            string(ExamplePacket::string)
            varInt(ExamplePacket::varInt)
            varLong(ExamplePacket::varLong)
            uuid(ExamplePacket::uuid)
            json(ExamplePacket::jsonComponent)
            nbt(ExamplePacket::nbtComponent)
            nbt(ExamplePacket::styledNbtComponent)
            position(ExamplePacket::position)
        }
    }

}

class CodecTest {

    private val packet = ExamplePacket(
        true,
        0.toByte(),
        0.0,
        0f,
        0,
        0L,
        0.toShort(),
        "Hello, World",
        0,
        0L,
        UUID.randomUUID(),
        Component.text("Hello, World"),
        Component.text("Hello, World"),
        Component.text("Hello, World")
            .style(Style.style(TextColor.color(255, 0, 255), ClickEvent.openUrl("https://0x1bd.github.io"))),
        Vec3i(42)
    )

    @Test
    fun `test example packet codec`() = runBlocking {
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

        val channel = ByteChannel()

        registry.getCodecFromId(0).encode(packet, channel)

        val decoded = registry.getCodecFromId(0).decode(channel)

        assertEquals(packet, decoded)
    }

}