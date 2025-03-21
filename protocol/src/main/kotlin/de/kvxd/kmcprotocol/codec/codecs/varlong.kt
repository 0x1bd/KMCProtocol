package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import kotlin.experimental.and

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/


object VarLongCodec : ElementCodec<Long> {

    override suspend fun encode(channel: ByteWriteChannel, value: Long) {
        var current = value
        do {
            val byte = (current and 0x7F).toByte()
            current = current ushr 7
            channel.writeByte(if (current != 0L) (byte.toInt() or 0x80).toByte() else byte)
        } while (current != 0L)
    }

    override suspend fun decode(channel: ByteReadChannel): Long {
        var value = 0L
        var bytesRead = 0
        var offset = 0
        var byte: Byte

        do {
            if (bytesRead >= 10) error("VarLong too long")
            byte = channel.readByte()
            val segment = byte.toLong() and 0x7F
            value = value or (segment shl offset)
            offset += 7
            bytesRead++
        } while ((byte and 0x80.toByte()) != 0.toByte())

        return value
    }
}