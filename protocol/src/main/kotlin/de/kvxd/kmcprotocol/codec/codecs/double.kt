package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object DoubleCodec : ElementCodec<Double> {

    override suspend fun encode(channel: ByteWriteChannel, value: Double) {
        channel.writeDouble(value)
    }

    override suspend fun decode(channel: ByteReadChannel): Double {
        return channel.readDouble()
    }
}