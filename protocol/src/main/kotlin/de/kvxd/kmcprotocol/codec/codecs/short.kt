package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object ShortCodec : ElementCodec<Short> {

    override suspend fun encode(channel: ByteWriteChannel, value: Short) {
        channel.writeShort(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Short {
        return channel.readShort()
    }
}