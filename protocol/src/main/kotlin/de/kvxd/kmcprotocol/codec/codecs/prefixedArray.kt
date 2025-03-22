package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

class PrefixedArrayCodec<E>(
    private val elementCodec: ElementCodec<E>
) : ElementCodec<List<E>> {

    override suspend fun encode(channel: ByteWriteChannel, value: List<E>) {
        VarIntCodec.encode(channel, value.size)

        value.forEach { element ->
            elementCodec.encode(channel, element)
        }
    }

    override suspend fun decode(channel: ByteReadChannel): List<E> {
        val size = VarIntCodec.decode(channel)

        if (size < 0) {
            throw IllegalArgumentException("Array size cannot be negative: $size")
        }
        return List(size) {
            elementCodec.decode(channel)
        }
    }
}