package de.kvxd.kmcprotocol.datatypes

import de.kvxd.kmcprotocol.serialization.MinecraftPacketDecoder
import de.kvxd.kmcprotocol.serialization.MinecraftPacketEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object VarIntSerializer: KSerializer<Int> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("VarInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        if (encoder is MinecraftPacketEncoder)
            encoder.encodeVarInt(value)
        else
            throw IllegalArgumentException("Unsupported encoder")
    }

    override fun deserialize(decoder: Decoder): Int {
        if (decoder is MinecraftPacketDecoder)
            return decoder.decodeVarInt()
        else
            throw IllegalArgumentException("Unsupported encoder")
    }

}