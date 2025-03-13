package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.io.Source
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class VarInt {

    companion object {

        fun encode(value: Int): ByteArray {
            var intVal = value
            val byteArray = ByteArray(5)
            var index = 0
            while (true) {
                if ((intVal and 0x7F.inv()) == 0) {
                    byteArray[index] = intVal.toByte()
                    return byteArray.copyOfRange(0, index + 1)
                }
                byteArray[index] = (intVal and 0x7F or 0x80).toByte()
                intVal = intVal ushr 7
                index++
            }
        }

        fun decode(input: Source): Int {
            var value = 0
            var position = 0
            var currentByte: Int
            while (true) {
                currentByte = input.readByte().toInt()
                value = value or ((currentByte and 0x7F) shl position)
                if ((currentByte and 0x80) == 0) break
                position += 7
                if (position >= 32) throw RuntimeException("VarInt is too big")
            }
            return value
        }
    }

    object Serializer: KSerializer<Int> {

        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("VarInt", PrimitiveKind.INT)

        override fun serialize(encoder: Encoder, value: Int) {
            if (encoder is MinecraftPacketEncoder)
                encoder.encodeVarInt(value)
            else
                throw IllegalArgumentException("Unsupported encoder")
        }

        override fun deserialize(decoder: Decoder): Int {
            if (decoder is MinecraftPacketDecoder)
                return decoder.decodeVarInt()
            else
                throw IllegalArgumentException("Unsupported encoder")
        }

    }
}