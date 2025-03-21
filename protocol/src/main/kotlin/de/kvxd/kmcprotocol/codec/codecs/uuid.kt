package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*
import java.util.*

object UUIDCodec : ElementCodec<UUID> {

    override suspend fun encode(channel: ByteWriteChannel, value: UUID) {
        channel.writeLong(value.mostSignificantBits)
        channel.writeLong(value.leastSignificantBits)
    }

    override suspend fun decode(channel: ByteReadChannel): UUID {
        return UUID(
            LongCodec.decode(channel), LongCodec.decode(channel)
        )
    }
}