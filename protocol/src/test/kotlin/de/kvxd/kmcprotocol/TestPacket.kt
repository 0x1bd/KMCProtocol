package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.variant.EValue
import de.kvxd.kmcprotocol.core.variant.EVariant
import de.kvxd.kmcprotocol.core.variant.NumVariant
import de.kvxd.kmcprotocol.core.variant.UseJson
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
    @UseJson
    val json: JsonStruct
) : MinecraftPacket {

    enum class TestEnum {

        @EValue(-12)
        One,
        Two,
        Three,
        Four

    }

    @EVariant(NumVariant.VarInt)
    enum class TestEnum1 {

        @EValue(64)
        One,

        @EValue(6)
        Two,

        @EValue(9)
        Three,

        @EValue(54)
        Four

    }

    @Serializable
    data class JsonStruct(
        val a: String,
        val b: Int,
        val c: String
    )

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
    TestPacket.JsonStruct("Hello", 0, "World")
)