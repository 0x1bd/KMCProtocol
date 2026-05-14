package org.kvxd.kmcprotocol.core.encoding

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readDouble
import io.ktor.utils.io.readFloat
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readLong
import io.ktor.utils.io.readShort
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeDouble
import io.ktor.utils.io.writeFloat
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeLong
import io.ktor.utils.io.writeShort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlin.experimental.and

/**
 * Numeric wire encodings supported by the Minecraft protocol.
 */
enum class NumberEncoding {
    /** Signed 8-bit integer. */
    Byte,
    /** Unsigned 8-bit integer. */
    UnsignedByte,
    /** Signed 16-bit integer. */
    Short,
    /** Unsigned 16-bit integer. */
    UnsignedShort,
    /** Signed 32-bit integer. */
    Int,
    /** Unsigned 32-bit integer. */
    UnsignedInt,
    /** Minecraft VarInt. */
    VarInt,
    /** Signed 64-bit integer. */
    Long,
    /** Minecraft VarLong. */
    VarLong,
    /** 32-bit floating-point number. */
    Float,
    /** 64-bit floating-point number. */
    Double,
}

/**
 * Overrides the numeric wire encoding for a generated or custom packet property.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolNumber(val encoding: NumberEncoding)

/**
 * Declares the numeric wire encoding used for enum values.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
annotation class ProtocolEnum(val encoding: NumberEncoding = NumberEncoding.VarInt)

/**
 * Declares the numeric wire value for a single enum entry.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class ProtocolEnumValue(val value: Int)

/**
 * Declares a PrismarineJS protocol type used to encode and decode a dynamic packet property.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolType(val json: String)

/**
 * Writes a numeric value using the selected Minecraft wire encoding.
 */
suspend fun ByteWriteChannel.writeNumber(encoding: NumberEncoding, value: Number) {
    when (encoding) {
        NumberEncoding.Byte -> writeByte(value.toByte())
        NumberEncoding.UnsignedByte -> writeByte(value.toInt().toByte())
        NumberEncoding.Short -> writeShort(value.toShort())
        NumberEncoding.UnsignedShort -> writeShort(value.toInt().toShort())
        NumberEncoding.Int -> writeInt(value.toInt())
        NumberEncoding.UnsignedInt -> writeInt(value.toLong().toInt())
        NumberEncoding.VarInt -> writeVarInt(value.toInt())
        NumberEncoding.Long -> writeLong(value.toLong())
        NumberEncoding.VarLong -> writeVarLong(value.toLong())
        NumberEncoding.Float -> writeFloat(value.toFloat())
        NumberEncoding.Double -> writeDouble(value.toDouble())
    }
}

/**
 * Reads a numeric value using the selected Minecraft wire encoding.
 */
suspend fun ByteReadChannel.readNumber(encoding: NumberEncoding): Number =
    when (encoding) {
        NumberEncoding.Byte -> readByte()
        NumberEncoding.UnsignedByte -> readByte().toUByte().toShort()
        NumberEncoding.Short -> readShort()
        NumberEncoding.UnsignedShort -> readShort().toUShort().toInt()
        NumberEncoding.Int -> readInt()
        NumberEncoding.UnsignedInt -> readInt().toUInt().toLong()
        NumberEncoding.VarInt -> readVarInt()
        NumberEncoding.Long -> readLong()
        NumberEncoding.VarLong -> readVarLong()
        NumberEncoding.Float -> readFloat()
        NumberEncoding.Double -> readDouble()
    }

/**
 * Writes a Minecraft VarInt.
 */
suspend fun ByteWriteChannel.writeVarInt(value: Int) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0)
}

/**
 * Reads a Minecraft VarInt.
 */
suspend fun ByteReadChannel.readVarInt(): Int {
    var offset = 0
    var value = 0
    var byte: Byte

    do {
        if (offset == 35) error("VarInt too long")

        byte = readByte()
        value = value or ((byte and 0x7F).toInt() shl offset)

        offset += 7
    } while ((byte and 0x80.toByte()) != 0.toByte())

    return value
}

/**
 * Writes a Minecraft VarLong.
 */
suspend fun ByteWriteChannel.writeVarLong(value: Long) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0L) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0L)
}

/**
 * Reads a Minecraft VarLong.
 */
suspend fun ByteReadChannel.readVarLong(): Long {
    var offset = 0
    var value = 0L
    var byte: Byte
    do {
        if (offset == 70) error("VarLong too long")
        byte = readByte()
        value = value or ((byte.toLong() and 0x7F) shl offset)
        offset += 7
    } while ((byte and 0x80.toByte()) != 0.toByte())
    return value
}
