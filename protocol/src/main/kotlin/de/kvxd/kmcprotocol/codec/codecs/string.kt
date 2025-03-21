package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

object StringCodec : ElementCodec<String> {

    override suspend fun encode(channel: ByteWriteChannel, value: String) {
        VarIntCodec.encode(channel, value.length)
        channel.writeByteArray(value.toByteArray(Charsets.UTF_8))
    }

    override suspend fun decode(channel: ByteReadChannel): String {
        val length = VarIntCodec.decode(channel)
        val bytes = ByteArray(length)
        channel.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}