package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

class PrefixedArrayCodec<P, E>(
    private val prefixCodec: ElementCodec<P>,
    private val elementCodec: ElementCodec<E>,
    private val sizeToPrefix: (Int) -> P = { it as P },
    private val prefixToSize: (P) -> Int = { it as Int }
) : ElementCodec<List<E>> {

    override suspend fun encode(channel: ByteWriteChannel, value: List<E>) {
        val prefixValue = sizeToPrefix(value.size)
        prefixCodec.encode(channel, prefixValue)
        value.forEach { element ->
            elementCodec.encode(channel, element)
        }
    }

    override suspend fun decode(channel: ByteReadChannel): List<E> {
        val prefixValue = prefixCodec.decode(channel)
        val size = prefixToSize(prefixValue)
        if (size < 0) {
            throw IllegalArgumentException("Array size cannot be negative: $size")
        }
        return List(size) {
            elementCodec.decode(channel)
        }
    }
}