package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import kotlin.experimental.and

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/

object VarIntCodec : ElementCodec<Int> {

    override suspend fun encode(channel: ByteWriteChannel, value: Int) {
        var current = value
        do {
            val byte = (current and 0x7F).toByte()
            current = current ushr 7
            channel.writeByte(if (current != 0) (byte.toInt() or 0x80).toByte() else byte)
        } while (current != 0)
    }

    override suspend fun decode(channel: ByteReadChannel): Int {
        var offset = 0
        var value = 0L
        var byte: Byte

        do {
            if (offset == 35) error("VarInt too long")

            byte = channel.readByte()
            value = value or ((byte.toLong() and 0x7FL) shl offset)

            offset += 7
        } while ((byte and 0x80.toByte()) != 0.toByte())

        return value.toInt()
    }

    suspend fun decodeOrNull(channel: ByteReadChannel): Int? = try {
        decode(channel)
    } catch (ignored: Exception) {
        null
    }
}