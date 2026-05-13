package org.kvxd.kmcprotocol

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.encoding.NumberEncoding
import org.kvxd.kmcprotocol.core.encoding.ProtocolEnum
import org.kvxd.kmcprotocol.core.encoding.ProtocolEnumValue
import kotlinx.serialization.Serializable

@Serializable
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
) : MinecraftPacket {

    enum class TestEnum {

        @ProtocolEnumValue(-12)
        One,
        Two,
        Three,
        Four

    }

    @ProtocolEnum(NumberEncoding.VarInt)
    enum class TestEnum1 {

        @ProtocolEnumValue(64)
        One,

        @ProtocolEnumValue(6)
        Two,

        @ProtocolEnumValue(9)
        Three,

        @ProtocolEnumValue(54)
        Four

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
    TestPacket.TestEnum1.Four
)