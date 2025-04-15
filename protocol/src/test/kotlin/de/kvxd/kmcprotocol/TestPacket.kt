package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.PacketMetadata
import de.kvxd.kmcprotocol.core.encoding.serializers.ComponentSerializer
import de.kvxd.kmcprotocol.core.format.EnumValue
import de.kvxd.kmcprotocol.core.format.number.IntFormat
import de.kvxd.kmcprotocol.core.format.number.IntFormatType
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component

@Serializable
@PacketMetadata(
    id = 0x00,
    direction = Direction.Serverbound
)
data class TestPacket(
    val byte: Byte,
    val boolean: Boolean,
    val char: Char,
    val double: Double,
    val float: Float,
    val int: Int,
    val varInt: Int,
    val long: Long,
    var varLong: Long,
    val short: Short,
    val string: String,
    val enum: TestEnum,
    val enum1: TestEnum1,
    val json: JsonStruct
) : MinecraftPacket {

    enum class TestEnum {

        @EnumValue(-12)
        One,

        @EnumValue(-13)
        Two,

        @EnumValue(-16)
        Three,

        @EnumValue(54)
        Four

    }

    @IntFormat(IntFormatType.VARIABLE)
    enum class TestEnum1 {

        @EnumValue(64)
        One,

        @EnumValue(6)
        Two,

        @EnumValue(9)
        Three,

        @EnumValue(54)
        Four

    }

    @Serializable(/*with = JsonStruct.Serializer::class*/)
    data class JsonStruct(
        val a: String,
        val b: Int,
        val c: String,
        @Serializable(with = ComponentSerializer::class)
        val d: Component
    ) {
        //object Serializer : KSerializer<JsonStruct> by jsonString()
    }

}

fun generateTestPacket() = TestPacket(
    0x00,
    true,
    'a',
    1.0,
    2f,
    42,
    84,
    42L,
    84L,
    16,
    "Hello, World!",
    TestPacket.TestEnum.One,
    TestPacket.TestEnum1.Four,
    TestPacket.JsonStruct("Hello", 0, "World", Component.text("Hello, World Component!"))
)