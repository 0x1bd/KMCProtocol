package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.KMCSerializer
import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.io.Source
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor

class VarLong {

    companion object {

        fun encode(value: Long): ByteArray {
            var longVal = value
            val byteArray = ByteArray(10)
            var index = 0
            while (true) {
                if ((longVal and 0x7F.inv()) == 0L) {
                    byteArray[index] = longVal.toByte()
                    return byteArray.copyOfRange(0, index + 1)
                }
                byteArray[index] = (longVal and 0x7F or 0x80).toByte()
                longVal = longVal ushr 7
                index++
            }
        }

        fun decode(input: Source): Long {
            var value = 0L
            var position = 0
            var currentByte: Int
            while (true) {
                currentByte = input.readByte().toInt()
                value = value or ((currentByte and 0x7F).toLong() shl position)
                if ((currentByte and 0x80) == 0) break
                position += 7
                if (position >= 64) throw RuntimeException("VarLong is too big")
            }
            return value
        }
    }

    object Serializer : KMCSerializer<Long>() {

        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("VarLong", PrimitiveKind.LONG)

        override fun serialize(encoder: MinecraftPacketEncoder, value: Long) {
            encoder.encodeVarLong(value)
        }

        override fun deserialize(decoder: MinecraftPacketDecoder): Long {
            return decoder.decodeVarLong()
        }
    }
}