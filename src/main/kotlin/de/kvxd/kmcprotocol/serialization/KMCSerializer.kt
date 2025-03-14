package de.kvxd.kmcprotocol.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class KMCSerializer<T> : KSerializer<T> {

    override fun deserialize(decoder: Decoder): T {
        if (decoder !is MinecraftPacketDecoder)
            throw IllegalArgumentException("Unsupported decoder")
        else

            return deserialize(decoder)
    }

    override fun serialize(encoder: Encoder, value: T) {
        if (encoder !is MinecraftPacketEncoder)
            throw IllegalArgumentException("Unsupported encoder")
        else

            serialize(encoder, value)
    }

    abstract fun serialize(encoder: MinecraftPacketEncoder, value: T)
    abstract fun deserialize(decoder: MinecraftPacketDecoder): T
}