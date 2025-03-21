package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object BooleanCodec : ElementCodec<Boolean> {

    override suspend fun encode(channel: ByteWriteChannel, value: Boolean) {
        channel.writeByte(if (value) 0x01 else 0x00)
    }

    override suspend fun decode(channel: ByteReadChannel): Boolean {
        return channel.readByte() == 0x01.toByte()
    }
}