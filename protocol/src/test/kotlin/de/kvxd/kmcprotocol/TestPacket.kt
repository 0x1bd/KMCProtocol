package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.codec.PacketCodec
import de.kvxd.kmcprotocol.codec.codecs.*
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketMetadata
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import java.util.*

enum class TestEnum {

    One,
    Two,
    Three,
    Four

}

@PacketMetadata(
    id = 0x00,
    direction = Direction.SERVERBOUND,
    state = ProtocolState.HANDSHAKE
)
data class TestPacket(
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
    var array: List<Vec3i>,
    var enum: TestEnum
) : MinecraftPacket {

    companion object {

        val CODEC = PacketCodec<TestPacket> {
            element(TestPacket::boolean, BooleanCodec)
            element(TestPacket::byte, ByteCodec)
            element(TestPacket::double, DoubleCodec)
            element(TestPacket::float, FloatCodec)
            element(TestPacket::int, IntCodec)
            element(TestPacket::long, LongCodec)
            element(TestPacket::short, ShortCodec)
            element(TestPacket::string, StringCodec)
            element(TestPacket::varInt, VarIntCodec)
            element(TestPacket::varLong, VarLongCodec)
            element(TestPacket::uuid, UUIDCodec)
            element(TestPacket::jsonComponent, JsonTextCodec)
            element(TestPacket::nbtComponent, NbtTextCodec)
            element(TestPacket::styledNbtComponent, NbtTextCodec)
            element(TestPacket::position, Vec3iCodec)
            element(TestPacket::array, PrefixedArrayCodec(Vec3iCodec))
            element(TestPacket::enum, enumCodec())
        }

        fun generateTestPacket(): TestPacket =
            TestPacket(
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
                Vec3i(42),
                listOf(
                    Vec3i(1),
                    Vec3i(2),
                    Vec3i(3)
                ),
                TestEnum.Four
            )
    }

}