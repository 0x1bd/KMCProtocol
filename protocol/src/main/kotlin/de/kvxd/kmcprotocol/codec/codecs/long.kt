package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object LongCodec : ElementCodec<Long> {

    override suspend fun encode(channel: ByteWriteChannel, value: Long) {
        channel.writeLong(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Long {
        return channel.readLong()
    }
}