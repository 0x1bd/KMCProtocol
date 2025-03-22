package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object UShortCodec : ElementCodec<Int> {

    override suspend fun encode(channel: ByteWriteChannel, value: Int) {
        require(value in 0..65535) { "Value $value is out of range for an unsigned short" }
        channel.writeByte((value shr 8).toByte())
        channel.writeByte(value.toByte())
    }

    override suspend fun decode(channel: ByteReadChannel): Int {
        val high = channel.readByte().toInt() and 0xFF
        val low = channel.readByte().toInt() and 0xFF
        return (high shl 8) or low
    }
}