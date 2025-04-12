package de.kvxd.kmcprotocol.core.variant

import de.kvxd.kmcprotocol.core.variant.NumVariant.VarInt
import io.ktor.utils.io.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlin.experimental.and

/**
 * Holds properties for determining the variant of a number.
 * [VarInt] is used as a default.
 */
enum class NumVariant {

    Int,

    // Default
    VarInt,

    Long,
    VarLong;

    companion object {

        suspend fun encodeInt(variant: NumVariant, value: kotlin.Int, channel: ByteWriteChannel) {
            when (variant) {
                Int -> channel.writeInt(value)
                VarInt -> channel.writeVarInt(value)

                Long, VarLong -> error("Cannot encode int with long or varlong variant")
            }
        }

        suspend fun decodeInt(variant: NumVariant, channel: ByteReadChannel): kotlin.Int =
            when (variant) {
                Int -> channel.readInt()
                VarInt -> channel.readVarInt()

                Long, VarLong -> error("Cannot decode int with long or varlong variant")
            }

        suspend fun encodeLong(variant: NumVariant, value: kotlin.Long, channel: ByteWriteChannel) {
            when (variant) {
                Long -> channel.writeLong(value)
                VarLong -> channel.writeVarLong(value)

                Int, VarInt -> error("Cannot encode long with int or varint variant")
            }
        }

        suspend fun decodeLong(variant: NumVariant, channel: ByteReadChannel): kotlin.Long {
            return when (variant) {
                Long -> channel.readLong()
                VarLong -> channel.readVarLong()

                Int, VarInt -> error("Cannot decode long with int or varint variant")
            }
        }


    }

}

suspend fun ByteWriteChannel.writeVarInt(value: Int) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0)
}

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

suspend fun ByteWriteChannel.writeVarLong(value: Long) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0L) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0L)
}

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

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class NV(val kind: NumVariant)