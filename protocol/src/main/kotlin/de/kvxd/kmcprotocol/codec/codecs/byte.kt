package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object ByteCodec : ElementCodec<Byte> {

    override suspend fun encode(channel: ByteWriteChannel, value: Byte) {
        channel.writeByte(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Byte {
        return channel.readByte()
    }
}