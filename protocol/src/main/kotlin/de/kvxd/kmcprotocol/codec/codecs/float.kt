package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object FloatCodec : ElementCodec<Float> {

    override suspend fun encode(channel: ByteWriteChannel, value: Float) {
        channel.writeFloat(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Float {
        return channel.readFloat()
    }
}