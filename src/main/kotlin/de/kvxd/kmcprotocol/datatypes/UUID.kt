package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

object UuidSerializer : KSerializer<UUID> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        if (decoder is MinecraftPacketDecoder)
            return decoder.decodeUUID()
        else
            throw IllegalArgumentException("Unsupported decoder")
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        if (encoder is MinecraftPacketEncoder)
            return encoder.encodeUUID(value)
        else
            throw IllegalArgumentException("Unsupported decoder")
    }

}