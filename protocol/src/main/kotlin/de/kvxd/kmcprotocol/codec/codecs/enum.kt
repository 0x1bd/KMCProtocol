package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import kotlin.reflect.KClass

class EnumCodec<T : Enum<T>>(private val enumClass: KClass<T>, private val offset: Int = 0) : ElementCodec<T> {

    override suspend fun encode(channel: ByteWriteChannel, value: T) {
        VarIntCodec.encode(channel, value.ordinal + offset)
    }

    override suspend fun decode(channel: ByteReadChannel): T {
        val ordinal = VarIntCodec.decode(channel) + offset
        return enumClass.java.enumConstants[ordinal]
    }
}

inline fun <reified T : Enum<T>> enumCodec(offset: Int = 0): EnumCodec<T> {
    return EnumCodec(T::class, offset)
}