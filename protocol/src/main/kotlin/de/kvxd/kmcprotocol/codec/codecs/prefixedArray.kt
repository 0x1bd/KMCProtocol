package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import kotlin.reflect.KClass

class PrefixedArrayCodec<E : Any>(
    private val elementCodec: ElementCodec<E>,
    private val elementClass: KClass<E> // Added to capture type at runtime
) : ElementCodec<Array<E>> {

    override suspend fun encode(channel: ByteWriteChannel, value: Array<E>) {
        VarIntCodec.encode(channel, value.size)
        value.forEach { elementCodec.encode(channel, it) }
    }

    override suspend fun decode(channel: ByteReadChannel): Array<E> {
        val size = VarIntCodec.decode(channel)
        if (size < 0) throw IllegalArgumentException("Array size cannot be negative: $size")

        // Create array using reflection
        @Suppress("UNCHECKED_CAST")
        val array = java.lang.reflect.Array.newInstance(elementClass.java, size) as Array<E>

        for (i in 0 until size) {
            array[i] = elementCodec.decode(channel)
        }
        return array
    }
}

inline fun <reified E : Any> prefixedArray(codec: ElementCodec<E>) = PrefixedArrayCodec(codec, E::class)