package de.kvxd.kmcprotocol.codec

import io.ktor.utils.io.*

interface ElementCodec<T> {

    suspend fun encode(channel: ByteWriteChannel, value: T)
    suspend fun decode(channel: ByteReadChannel): T

}