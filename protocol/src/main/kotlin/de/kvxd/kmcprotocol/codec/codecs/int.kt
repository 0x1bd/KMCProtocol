package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object IntCodec : ElementCodec<Int> {

    override suspend fun encode(channel: ByteWriteChannel, value: Int) {
        channel.writeInt(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Int {
        return channel.readInt()
    }
}